package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.chunk.ChunkingOptions;
import com.zjl.knowledge.chunk.ChunkingStrategy;
import com.zjl.knowledge.chunk.ChunkingStrategyFactory;
import com.zjl.knowledge.chunk.TextChunk;
import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.domain.ProcessMode;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.event.DocumentChunkRequestedEvent;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.metadata.ChunkMetadata;
import com.zjl.knowledge.metadata.SensitivityKeywordService;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.preprocess.DocumentPreprocessingContext;
import com.zjl.knowledge.preprocess.DocumentPreprocessingResult;
import com.zjl.knowledge.preprocess.DocumentPreprocessor;
import com.zjl.knowledge.preprocess.DocumentPreprocessorSelector;
import com.zjl.knowledge.service.DocumentChunkingService;
import com.zjl.knowledge.service.FileStorageService;
import com.zjl.knowledge.service.TikaDocumentParser;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.token.TokenCounterService;
import com.zjl.knowledge.util.ContentHashUtil;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文档分块服务：异步分块任务（Tika 解析 → 策略分块 → 向量化 → Milvus 写入 → DB 持久化）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkingServiceImpl implements DocumentChunkingService {

    private final KbDocumentMapper kbDocumentMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentChunkLogMapper kbDocumentChunkLogMapper;
    private final TikaDocumentParser tikaDocumentParser;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final VectorSyncService vectorSyncService;
    private final TokenCounterService tokenCounterService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final SensitivityKeywordService sensitivityKeywordService;
    private final DocumentPreprocessorSelector documentPreprocessorSelector;

    /**
     * 提交异步分块：CAS 更新 status→RUNNING，事务提交后由监听器异步执行
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void startChunk(Long documentId, UserContext user) {
        KbDocument current = kbDocumentMapper.selectById(documentId);
        if (current == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        // 严格限定只能从 PENDING 或 FAILED 状态转换为 RUNNING
        if (!DocumentStatus.PENDING.name().equals(current.getStatus())
                && !DocumentStatus.FAILED.name().equals(current.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 PENDING 或 FAILED 状态的文档可提交分块任务");
        }
        // CAS 更新：严格限定当前状态必须是 PENDING 或 FAILED
        int rows = kbDocumentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .set(KbDocument::getStatus, DocumentStatus.RUNNING.name())
                .set(KbDocument::getUpdatedAt, LocalDateTime.now())
                .eq(KbDocument::getId, documentId)
                .and(wrapper -> wrapper
                        .eq(KbDocument::getStatus, DocumentStatus.PENDING.name())
                        .or()
                        .eq(KbDocument::getStatus, DocumentStatus.FAILED.name())));
        if (rows == 0) {
            KbDocument doc = kbDocumentMapper.selectById(documentId);
            if (doc == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "文档分块操作正在进行中，请稍后再试");
        }
        applicationEventPublisher.publishEvent(new DocumentChunkRequestedEvent(documentId, user.getUserId()));
    }

    /**
     * 同步执行分块（补偿用）
     */
    @Override
    public void executeChunk(Long documentId, Long operatorUserId) {
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null) {
            log.warn("文档不存在，跳过分块任务, documentId={}", documentId);
            return;
        }
        runChunkTask(document, operatorUserId);
    }

    /**
     * 校验写权限后执行分块
     */
    @Override
    public void executeChunkAsUser(Long documentId, UserContext user) {
        KbDocument doc = kbDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!user.isAdmin() && !Objects.equals(doc.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        executeChunk(documentId, user.getUserId());
    }

    private void runChunkTask(KbDocument document, Long operatorUserId) {
        Long docId = document.getId();
        Long acting = operatorUserId != null ? operatorUserId : document.getOwnerId();
        ProcessMode processMode = ProcessMode.normalize(document.getProcessMode());

        KbDocumentChunkLog chunkLog = new KbDocumentChunkLog();
        chunkLog.setDocumentId(docId);
        chunkLog.setStatus(DocumentStatus.RUNNING.name());
        chunkLog.setProcessMode(processMode.name());
        chunkLog.setChunkStrategy(document.getChunkStrategy());
        chunkLog.setPipelineId(document.getPipelineId());
        chunkLog.setChunkCount(0);
        chunkLog.setStartedAt(LocalDateTime.now());
        kbDocumentChunkLogMapper.insert(chunkLog);

        long totalStart = System.currentTimeMillis();
        long extractDuration = 0;
        long chunkDuration = 0;
        long embedDuration = 0;
        long persistDuration = 0;

        try {
            if (processMode == ProcessMode.PIPELINE) {
                throw new BizException(ErrorCode.PARAM_INVALID, "PIPELINE 模式尚未接入，请使用 CHUNK");
            }

            long extractStart = System.currentTimeMillis();
            String text;
            java.util.Map<String, String> docMetadata = java.util.Map.of();
            try (InputStream is = fileStorageService.read(document.getFileUrl())) {
                TikaDocumentParser.ParseResult parseResult = tikaDocumentParser.extractWithMetadata(
                        is, document.getFileName(), document.getFileType());
                text = parseResult.text();
                docMetadata = parseResult.metadata();
            } catch (IOException | TikaException | SAXException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文档解析失败: " + ex.getMessage());
            }
            extractDuration = System.currentTimeMillis() - extractStart;
            if (!StringUtils.hasText(text)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未能从文件中提取正文");
            }

            DocumentPreprocessingContext preprocessingContext =
                    new DocumentPreprocessingContext(document, text, docMetadata);
            DocumentPreprocessor preprocessor = documentPreprocessorSelector.select(preprocessingContext);
            DocumentPreprocessingResult preprocessingResult = preprocessor.preprocess(preprocessingContext);
            String chunkInputText = preprocessingResult.chunkInputText();
            if (!StringUtils.hasText(chunkInputText)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文档预处理后正文为空");
            }

            document.setMetadata(toJson(mergeMetadata(docMetadata, preprocessingResult.documentMetadata())));

            ChunkingMode chunkingMode = ChunkingMode.fromValue(document.getChunkStrategy());
            ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(chunkingMode);
            ChunkingOptions options = ChunkingOptions.fromMap(parseChunkConfig(document.getChunkConfig()));

            long chunkStart = System.currentTimeMillis();
            List<TextChunk> parts = strategy.chunk(chunkInputText, options);
            chunkDuration = System.currentTimeMillis() - chunkStart;

            boolean shouldEmbed = vectorSyncService.shouldEmbed(document);
            List<List<Float>> vectors = null;

            if (shouldEmbed) {
                long embedStart = System.currentTimeMillis();
                List<String> texts = parts.stream().map(TextChunk::content).collect(Collectors.toList());
                vectors = vectorSyncService.embedBatch(texts, document);
                embedDuration = System.currentTimeMillis() - embedStart;

                if (vectors.size() != parts.size()) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "向量结果数量与分块数不一致");
                }
            }

            List<VectorDocChunk> chunkResults = new ArrayList<>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                long chunkPk = IdWorker.getId();
                TextChunk part = parts.get(i);

                ChunkMetadata meta = new ChunkMetadata();
                meta.setDocId(docId);
                meta.setFileName(document.getFileName());
                meta.setSourceUrl(document.getSourceLocation());
                meta.setChunkIndex(part.index());
                meta.setStartOffset(part.startOffset());
                meta.setEndOffset(part.endOffset());
                meta.setSensitivityLevel("ALL");

                if (sensitivityKeywordService.isSensitive(part.content())) {
                    meta.setSensitivityLevel("ADMIN_ONLY");
                }

                Map<String, Object> chunkMetadata = new LinkedHashMap<>(meta.toMap());
                chunkMetadata.putAll(preprocessingResult.chunkMetadataDefaults());

                VectorDocChunk.VectorDocChunkBuilder builder = VectorDocChunk.builder()
                        .chunkId(String.valueOf(chunkPk))
                        .content(part.content())
                        .index(part.index())
                        .metadata(chunkMetadata);

                if (shouldEmbed && vectors != null) {
                    builder.embedding(toArray(vectors.get(i)));
                }
                chunkResults.add(builder.build());
            }

            long persistStart = System.currentTimeMillis();
            int saved = persistChunksAndVectorsAtomically(document, acting, chunkResults, chunkInputText, shouldEmbed);
            persistDuration = System.currentTimeMillis() - persistStart;

            long totalDuration = System.currentTimeMillis() - totalStart;
            updateChunkLog(chunkLog.getId(), DocumentStatus.SUCCESS.name(), saved,
                    extractDuration, chunkDuration, embedDuration, persistDuration, totalDuration, null);
        } catch (Exception e) {
            log.error("文档分块任务执行失败：docId={}", docId, e);
            markChunkFailed(docId, e.getMessage());
            long totalDuration = System.currentTimeMillis() - totalStart;
            updateChunkLog(chunkLog.getId(), DocumentStatus.FAILED.name(), 0,
                    extractDuration, chunkDuration, embedDuration, persistDuration, totalDuration, e.getMessage());
        }
    }

    private int persistChunksAndVectorsAtomically(
            KbDocument document,
            Long actingUserId,
            List<VectorDocChunk> vectorChunks,
            String fullText,
            boolean shouldEmbed
    ) {
        final Long docId = document.getId();
        
        // 第一步：在事务中保存 chunk 数据到数据库
        int saved = saveChunksToDatabase(docId, actingUserId, vectorChunks, fullText);
        
        // 第二步：事务成功后，写入 Milvus 向量（外部操作放在事务外）
        if (shouldEmbed) {
            try {
                // 删除旧向量（如果存在）
                vectorSyncService.deleteDocumentVectors(document);
                // 写入新向量
                vectorSyncService.indexDocumentChunks(document, vectorChunks);
                log.info("Milvus 向量写入成功: docId={}, chunkCount={}", docId, vectorChunks.size());
            } catch (Exception ex) {
                // Milvus 写入失败，标记文档状态为部分成功，并记录错误
                log.error("Milvus 向量写入失败: docId={}, error={}", docId, ex.getMessage());
                markChunkFailed(docId, "向量写入失败: " + ex.getMessage());
                throw new BizException(ErrorCode.VECTOR_WRITE_FAILED, "向量写入失败: " + ex.getMessage());
            }
        }
        
        return saved;
    }

    /**
     * 在事务中保存 chunk 数据到数据库
     */
    @Transactional(rollbackFor = Exception.class)
    private int saveChunksToDatabase(Long docId, Long actingUserId, 
            List<VectorDocChunk> vectorChunks, String fullText) {
        kbDocumentChunkMapper.delete(new LambdaQueryWrapper<KbDocumentChunk>().eq(KbDocumentChunk::getDocumentId, docId));
        LocalDateTime now = LocalDateTime.now();
        for (VectorDocChunk vc : vectorChunks) {
            KbDocumentChunk row = new KbDocumentChunk();
            long id = Long.parseLong(vc.getChunkId());
            row.setId(id);
            row.setDocumentId(docId);
            row.setChunkIndex(vc.getIndex());
            row.setChunkText(vc.getContent());
            row.setContentHash(ContentHashUtil.sha256Hex(vc.getContent()));
            row.setCharCount(vc.getContent().length());
            row.setTokenCount(tokenCounterService.countTokens(vc.getContent()));
            row.setEnabled(1);
            row.setVectorId(vc.getEmbedding() != null ? vc.getChunkId() : null);
            Map<String, Object> chunkMeta = vc.getMetadata();
            if (chunkMeta != null && !chunkMeta.isEmpty()) {
                try {
                    row.setMetadataJson(objectMapper.writeValueAsString(chunkMeta));
                } catch (Exception e) {
                    row.setMetadataJson("{}");
                }
            } else {
                row.setMetadataJson("{}");
            }
            row.setCreatedBy(actingUserId);
            row.setUpdatedBy(actingUserId);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            kbDocumentChunkMapper.insert(row);
        }

        kbDocumentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, docId)
                .set(KbDocument::getContentText, fullText)
                .set(KbDocument::getSummary, tikaDocumentParser.summarize(fullText, 200))
                .set(KbDocument::getChunkCount, vectorChunks.size())
                .set(KbDocument::getStatus, DocumentStatus.SUCCESS.name())
                .set(KbDocument::getUpdatedAt, LocalDateTime.now()));
        
        return vectorChunks.size();
    }

    private void updateChunkLog(
            Long logId, String status, int chunkCount,
            long extractDuration, long chunkDuration, long embedDuration,
            long persistDuration, long totalDuration, String errorMessage
    ) {
        KbDocumentChunkLog u = new KbDocumentChunkLog();
        u.setId(logId);
        u.setStatus(status);
        u.setChunkCount(chunkCount);
        u.setExtractDurationMs(extractDuration);
        u.setChunkDurationMs(chunkDuration);
        u.setEmbedDurationMs(embedDuration);
        u.setPersistDurationMs(persistDuration);
        u.setTotalDurationMs(totalDuration);
        u.setErrorMessage(errorMessage);
        u.setEndedAt(LocalDateTime.now());
        kbDocumentChunkLogMapper.updateById(u);
    }

    private void markChunkFailed(Long docId, String reason) {
        transactionTemplate.executeWithoutResult(status ->
                kbDocumentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                        .eq(KbDocument::getId, docId)
                        .set(KbDocument::getStatus, DocumentStatus.FAILED.name())
                        .set(KbDocument::getSummary, reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason)
                        .set(KbDocument::getUpdatedAt, LocalDateTime.now())));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private Map<String, Object> parseChunkConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("分块参数解析失败: {}", json, e);
            return Map.of();
        }
    }

    private Map<String, Object> mergeMetadata(Map<String, String> parsedMetadata, Map<String, Object> preprocessingMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (parsedMetadata != null) {
            merged.putAll(parsedMetadata);
        }
        if (preprocessingMetadata != null) {
            merged.putAll(preprocessingMetadata);
        }
        return merged;
    }
}
