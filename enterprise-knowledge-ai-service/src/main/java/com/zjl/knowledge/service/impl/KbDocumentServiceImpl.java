package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.chunk.ChunkingOptions;
import com.zjl.knowledge.chunk.ChunkingStrategy;
import com.zjl.knowledge.chunk.ChunkingStrategyFactory;
import com.zjl.knowledge.chunk.TextChunk;
import com.zjl.knowledge.config.KbStorageProperties;
import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.domain.DocumentPermissionType;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.domain.ProcessMode;
import com.zjl.knowledge.domain.SourceType;
import com.zjl.knowledge.dto.KbDocumentChunkLogVO;
import com.zjl.knowledge.dto.KbDocumentUpdateRequest;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import com.zjl.knowledge.embedding.EmbeddingService;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.event.DocumentChunkRequestedEvent;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import com.zjl.knowledge.milvus.ChunkVectorStore;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.KbCategoryService;
import com.zjl.knowledge.service.KbChunkService;
import com.zjl.knowledge.service.KbDocumentService;
import com.zjl.knowledge.service.KbMilvusRoutingService;
import com.zjl.knowledge.service.TikaDocumentParser;
import com.zjl.knowledge.token.TokenCounterService;
import com.zjl.knowledge.util.ContentHashUtil;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识文档服务：上传（PENDING）→ 异步分块（RUNNING）→ 入库与向量（SUCCESS），行为对齐参考工程主干流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentService {

    private final KbCategoryService kbCategoryService;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentChunkLogMapper kbDocumentChunkLogMapper;
    private final TikaDocumentParser tikaDocumentParser;
    private final ChunkVectorStore chunkVectorStore;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final EmbeddingService embeddingService;
    private final TokenCounterService tokenCounterService;
    private final KbStorageProperties kbStorageProperties;
    private final DocumentVisibilityService documentVisibilityService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final KbChunkService kbChunkService;
    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final KbMilvusRoutingService kbMilvusRoutingService;

    @Override
    public IPage<KbDocument> pageVisible(Page<KbDocument> page, UserContext user) {
        Long deptId = user.getDepartmentId();
        Long projectId = user.getProjectId();
        int admin = user.isAdmin() ? 1 : 0;
        return baseMapper.selectPageVisible(page, user.getUserId(), deptId, projectId, admin);
    }

    @Override
    public KbDocument getVisible(Long id, UserContext user) {
        KbDocument doc = getById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<KbDocumentPermission> perms = kbDocumentPermissionMapper.selectList(
                new LambdaQueryWrapper<KbDocumentPermission>().eq(KbDocumentPermission::getDocumentId, id)
        );
        if (!documentVisibilityService.canView(doc, user, perms)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        SourceType sourceType = SourceType.normalize(meta.getSourceType());
        if (sourceType == SourceType.URL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "URL 来源上传尚未支持，请使用 FILE 上传");
        }
        validateSchedule(meta, sourceType);

        DocumentPermissionType permType;
        try {
            permType = DocumentPermissionType.valueOf(meta.getPermissionType());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "permissionType 非法");
        }
        if (permType == DocumentPermissionType.USER
                && (meta.getGrantUserIds() == null || meta.getGrantUserIds().isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "USER 权限必须指定 grantUserIds");
        }
        if (permType == DocumentPermissionType.PROJECT && meta.getGrantProjectId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "PROJECT 权限必须指定 grantProjectId");
        }
        if (kbCategoryService.getById(meta.getCategoryId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        if (meta.getKbId() != null) {
            KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(meta.getKbId());
            if (kb == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
            }
        }

        ProcessMode processMode = ProcessMode.normalize(meta.getProcessMode());
        ChunkingMode chunkingMode = ChunkingMode.fromValue(meta.getChunkStrategy());
        String chunkConfigJson = normalizeChunkConfigJson(chunkingMode, meta.getChunkConfig());

        KbDocument doc = new KbDocument();
        doc.setTitle(meta.getTitle().trim());
        doc.setCategoryId(meta.getCategoryId());
        doc.setKbId(meta.getKbId());
        doc.setOwnerId(user.getUserId());
        doc.setDepartmentId(user.getDepartmentId());
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setTags(meta.getTags());
        doc.setPermissionType(permType.name());
        doc.setStatus(DocumentStatus.PENDING.name());
        doc.setCurrentVersion(1);
        doc.setChunkCount(0);
        doc.setEnabled(1);
        doc.setProcessMode(processMode.name());
        doc.setChunkStrategy(chunkingMode.name());
        doc.setChunkConfig(chunkConfigJson);
        doc.setPipelineId(meta.getPipelineId());
        doc.setSourceType(sourceType.name());
        doc.setSourceLocation(StringUtils.hasText(meta.getSourceLocation()) ? meta.getSourceLocation().trim() : null);
        doc.setScheduleEnabled(Boolean.TRUE.equals(meta.getScheduleEnabled()) ? 1 : 0);
        doc.setScheduleCron(StringUtils.hasText(meta.getScheduleCron()) ? meta.getScheduleCron().trim() : null);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        save(doc);

        Path baseDir = Paths.get(kbStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path dir = baseDir.resolve(doc.getId().toString());
        try {
            Files.createDirectories(dir);
            String safeName = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin"));
            if (safeName.contains("..")) {
                throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名");
            }
            Path target = dir.resolve(safeName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            doc.setFileUrl(target.toString());
            byte[] probe = new byte[8192];
            int probeLen;
            try (InputStream probeIn = Files.newInputStream(target)) {
                probeLen = probeIn.read(probe);
            }
            if (probeLen > 0) {
                String detected = tikaDocumentParser.detectMime(java.util.Arrays.copyOf(probe, probeLen), safeName);
                if (StringUtils.hasText(detected)) {
                    doc.setFileType(detected);
                }
            }
            doc.setUpdatedAt(LocalDateTime.now());
            updateById(doc);

            savePermissionRows(doc.getId(), permType, meta, user.getUserId());
            return doc.getId();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            markUploadFailed(doc.getId(), ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件保存失败: " + ex.getMessage());
        }
    }

    private void markUploadFailed(Long docId, String reason) {
        baseMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, docId)
                .set(KbDocument::getStatus, DocumentStatus.FAILED.name())
                .set(KbDocument::getSummary, reason)
                .set(KbDocument::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startChunk(Long documentId, UserContext user) {
        assertWritable(documentId, user);
        KbDocument current = getById(documentId);
        if (current == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!DocumentStatus.PENDING.name().equals(current.getStatus())
                && !DocumentStatus.FAILED.name().equals(current.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 PENDING 或 FAILED 状态的文档可提交分块任务");
        }
        int rows = baseMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .set(KbDocument::getStatus, DocumentStatus.RUNNING.name())
                .set(KbDocument::getUpdatedAt, LocalDateTime.now())
                .eq(KbDocument::getId, documentId)
                .ne(KbDocument::getStatus, DocumentStatus.RUNNING.name()));
        if (rows == 0) {
            KbDocument doc = getById(documentId);
            if (doc == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "文档分块操作正在进行中，请稍后再试");
        }
        applicationEventPublisher.publishEvent(new DocumentChunkRequestedEvent(documentId, user.getUserId()));
    }

    @Override
    public void executeChunk(Long documentId, Long operatorUserId) {
        KbDocument document = getById(documentId);
        if (document == null) {
            log.warn("文档不存在，跳过分块任务, documentId={}", documentId);
            return;
        }
        runChunkTask(document, operatorUserId);
    }

    @Override
    public void executeChunkAsUser(Long documentId, UserContext user) {
        assertWritable(documentId, user);
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
            List<VectorDocChunk> chunkResults;
            if (processMode == ProcessMode.PIPELINE) {
                throw new BizException(ErrorCode.PARAM_INVALID, "PIPELINE 模式尚未接入，请使用 CHUNK");
            }

            long extractStart = System.currentTimeMillis();
            String text;
            try (InputStream is = Files.newInputStream(Paths.get(document.getFileUrl()))) {
                text = tikaDocumentParser.extractText(is, document.getFileName(), document.getFileType());
            } catch (IOException | TikaException | SAXException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文档解析失败: " + ex.getMessage());
            }
            extractDuration = System.currentTimeMillis() - extractStart;
            if (!StringUtils.hasText(text)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未能从文件中提取正文");
            }

            ChunkingMode chunkingMode = ChunkingMode.fromValue(document.getChunkStrategy());
            ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(chunkingMode);
            ChunkingOptions options = ChunkingOptions.fromMap(parseChunkConfig(document.getChunkConfig()));

            long chunkStart = System.currentTimeMillis();
            List<TextChunk> parts = strategy.chunk(text, options);
            chunkDuration = System.currentTimeMillis() - chunkStart;

            boolean shouldEmbed = kbMilvusRoutingService.shouldEmbed(document);
            List<List<Float>> vectors = null;
            
            if (shouldEmbed) {
                long embedStart = System.currentTimeMillis();
                List<String> texts = parts.stream().map(TextChunk::content).collect(Collectors.toList());
                vectors = embedBatch(texts, document);
                embedDuration = System.currentTimeMillis() - embedStart;

                if (vectors.size() != parts.size()) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "向量结果数量与分块数不一致");
                }
            }

            chunkResults = new ArrayList<>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                long chunkPk = IdWorker.getId();
                VectorDocChunk.VectorDocChunkBuilder builder = VectorDocChunk.builder()
                        .chunkId(String.valueOf(chunkPk))
                        .content(parts.get(i).content())
                        .index(parts.get(i).index());
                
                if (shouldEmbed && vectors != null) {
                    builder.embedding(toPrimitive(vectors.get(i)));
                }
                chunkResults.add(builder.build());
            }

            long persistStart = System.currentTimeMillis();
            String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
            int saved = persistChunksAndVectorsAtomically(docId, acting, chunkResults, text, milvusCollection, shouldEmbed);
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
            Long docId,
            Long actingUserId,
            List<VectorDocChunk> vectorChunks,
            String fullText,
            String milvusCollection,
            boolean shouldEmbed
    ) {
        final int[] count = {0};
        transactionTemplate.executeWithoutResult(status -> {
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
                row.setVectorId(shouldEmbed ? vc.getChunkId() : null);
                row.setMetadataJson("{}");
                row.setCreatedBy(actingUserId);
                row.setUpdatedBy(actingUserId);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                kbDocumentChunkMapper.insert(row);
            }
            if (shouldEmbed) {
                chunkVectorStore.deleteDocumentVectors(milvusCollection, docId);
                chunkVectorStore.indexDocumentChunks(milvusCollection, docId, vectorChunks);
            }

            baseMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                    .eq(KbDocument::getId, docId)
                    .set(KbDocument::getContentText, fullText)
                    .set(KbDocument::getSummary, tikaDocumentParser.summarize(fullText, 200))
                    .set(KbDocument::getChunkCount, vectorChunks.size())
                    .set(KbDocument::getStatus, DocumentStatus.SUCCESS.name())
                    .set(KbDocument::getUpdatedAt, LocalDateTime.now()));
            count[0] = vectorChunks.size();
        });
        return count[0];
    }

    private void updateChunkLog(
            Long logId,
            String status,
            int chunkCount,
            long extractDuration,
            long chunkDuration,
            long embedDuration,
            long persistDuration,
            long totalDuration,
            String errorMessage
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
                baseMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                        .eq(KbDocument::getId, docId)
                        .set(KbDocument::getStatus, DocumentStatus.FAILED.name())
                        .set(KbDocument::getSummary, reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason)
                        .set(KbDocument::getUpdatedAt, LocalDateTime.now())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocument(Long documentId, KbDocumentUpdateRequest request, UserContext user) {
        assertWritable(documentId, user);
        KbDocument doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (DocumentStatus.RUNNING.name().equals(doc.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块中，无法修改");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档标题不能为空");
        }
        LambdaUpdateWrapper<KbDocument> uw = Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, documentId)
                .set(KbDocument::getTitle, request.getTitle().trim())
                .set(KbDocument::getUpdatedAt, LocalDateTime.now());

        if (StringUtils.hasText(request.getProcessMode())) {
            ProcessMode pm = ProcessMode.normalize(request.getProcessMode());
            uw.set(KbDocument::getProcessMode, pm.name());
            if (pm == ProcessMode.CHUNK) {
                ChunkingMode cm = ChunkingMode.fromValue(request.getChunkStrategy());
                String cfg = normalizeChunkConfigJson(cm, request.getChunkConfig());
                uw.set(KbDocument::getChunkStrategy, cm.name());
                uw.set(KbDocument::getChunkConfig, cfg);
                uw.set(KbDocument::getPipelineId, null);
            } else {
                if (!StringUtils.hasText(request.getPipelineId())) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "使用 PIPELINE 模式时必须指定 pipelineId");
                }
                uw.set(KbDocument::getPipelineId, request.getPipelineId().trim());
                uw.set(KbDocument::getChunkStrategy, null);
                uw.set(KbDocument::getChunkConfig, null);
            }
        }
        if (request.getScheduleEnabled() != null) {
            uw.set(KbDocument::getScheduleEnabled, request.getScheduleEnabled());
        }
        if (StringUtils.hasText(request.getScheduleCron())) {
            uw.set(KbDocument::getScheduleCron, request.getScheduleCron().trim());
        }
        if (StringUtils.hasText(request.getSourceLocation())) {
            uw.set(KbDocument::getSourceLocation, request.getSourceLocation().trim());
        }
        baseMapper.update(null, uw);
    }

    @Override
    public void enableDocument(Long documentId, boolean enabled, UserContext user) {
        getVisible(documentId, user);
        assertWritable(documentId, user);
        KbDocument doc = getById(documentId);
        if (DocumentStatus.RUNNING.name().equals(doc.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块中，无法修改");
        }
        int target = enabled ? 1 : 0;
        if (doc.getEnabled() != null && doc.getEnabled() == target) {
            return;
        }

        boolean shouldEmbed = kbMilvusRoutingService.shouldEmbed(doc);
        List<KbDocumentChunk> chunks = kbDocumentChunkMapper.selectList(
                Wrappers.lambdaQuery(KbDocumentChunk.class)
                        .eq(KbDocumentChunk::getDocumentId, documentId)
                        .orderByAsc(KbDocumentChunk::getChunkIndex)
        );
        List<VectorDocChunk> vectorChunks = null;
        if (enabled && shouldEmbed) {
            if (chunks.isEmpty()) {
                log.warn("启用文档时未找到任何 Chunk，跳过向量重建, documentId={}", documentId);
            } else {
                vectorChunks = chunks.stream()
                        .map(c -> VectorDocChunk.builder()
                                .chunkId(String.valueOf(c.getId()))
                                .content(c.getChunkText())
                                .index(c.getChunkIndex())
                                .build())
                        .collect(Collectors.toList());
                List<String> texts = vectorChunks.stream().map(VectorDocChunk::getContent).collect(Collectors.toList());
                List<List<Float>> vecs = embedBatch(texts, doc);
                for (int i = 0; i < vectorChunks.size(); i++) {
                    vectorChunks.get(i).setEmbedding(toPrimitive(vecs.get(i)));
                }
            }
        }

        final List<VectorDocChunk> finalChunks = vectorChunks;
        String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(doc);
        transactionTemplate.executeWithoutResult(status -> {
            doc.setEnabled(target);
            doc.setUpdatedAt(LocalDateTime.now());
            updateById(doc);
            kbChunkService.updateEnabledByDocId(documentId, enabled, user.getUserId());
            if (shouldEmbed) {
                if (!enabled) {
                    chunkVectorStore.deleteDocumentVectors(milvusCollection, documentId);
                } else if (finalChunks != null && !finalChunks.isEmpty()) {
                    chunkVectorStore.indexDocumentChunks(milvusCollection, documentId, finalChunks);
                }
            }
        });
    }

    @Override
    public IPage<KbDocumentChunkLogVO> pageChunkLogs(Long documentId, long current, long size, UserContext user) {
        getVisible(documentId, user);
        Page<KbDocumentChunkLog> p = new Page<>(current, size);
        IPage<KbDocumentChunkLog> raw = kbDocumentChunkLogMapper.selectPage(p,
                Wrappers.lambdaQuery(KbDocumentChunkLog.class)
                        .eq(KbDocumentChunkLog::getDocumentId, documentId)
                        .orderByDesc(KbDocumentChunkLog::getStartedAt));
        return raw.convert(this::toChunkLogVo);
    }

    private KbDocumentChunkLogVO toChunkLogVo(KbDocumentChunkLog e) {
        KbDocumentChunkLogVO vo = new KbDocumentChunkLogVO();
        vo.setId(e.getId());
        vo.setDocumentId(e.getDocumentId());
        vo.setStatus(e.getStatus());
        vo.setProcessMode(e.getProcessMode());
        vo.setChunkStrategy(e.getChunkStrategy());
        vo.setPipelineId(e.getPipelineId());
        vo.setChunkCount(e.getChunkCount());
        vo.setExtractDurationMs(e.getExtractDurationMs());
        vo.setChunkDurationMs(e.getChunkDurationMs());
        vo.setEmbedDurationMs(e.getEmbedDurationMs());
        vo.setPersistDurationMs(e.getPersistDurationMs());
        vo.setTotalDurationMs(e.getTotalDurationMs());
        vo.setErrorMessage(e.getErrorMessage());
        vo.setStartedAt(e.getStartedAt());
        vo.setEndedAt(e.getEndedAt());
        return vo;
    }

    @Override
    public List<KbDocument> searchDocuments(UserContext user, String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<KbDocument> q = Wrappers.lambdaQuery(KbDocument.class)
                .like(KbDocument::getTitle, keyword.trim())
                .orderByDesc(KbDocument::getUpdatedAt)
                .last("LIMIT " + size);
        if (!user.isAdmin()) {
            q.eq(KbDocument::getOwnerId, user.getUserId());
        }
        return list(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVisible(Long id, UserContext user) {
        KbDocument doc = getById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!user.isAdmin() && !Objects.equals(doc.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (DocumentStatus.RUNNING.name().equals(doc.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块中，无法删除");
        }
        try {
            String milvusCollection = kbMilvusRoutingService.collectionForVectorWriteOrDefault(doc);
            chunkVectorStore.deleteDocumentVectors(milvusCollection, id);
        } catch (BizException ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "删除向量失败，文档未删除: " + ex.getMessage());
        }
        kbDocumentChunkMapper.delete(new LambdaQueryWrapper<KbDocumentChunk>().eq(KbDocumentChunk::getDocumentId, id));
        kbDocumentChunkLogMapper.delete(new LambdaQueryWrapper<KbDocumentChunkLog>().eq(KbDocumentChunkLog::getDocumentId, id));
        kbDocumentPermissionMapper.delete(
                new LambdaQueryWrapper<KbDocumentPermission>().eq(KbDocumentPermission::getDocumentId, id)
        );
        removeById(id);
    }

    private void assertWritable(Long documentId, UserContext user) {
        KbDocument doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!user.isAdmin() && !Objects.equals(doc.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateSchedule(KbDocumentUploadRequest meta, SourceType sourceType) {
        if (!Boolean.TRUE.equals(meta.getScheduleEnabled())) {
            return;
        }
        if (sourceType != SourceType.URL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 URL 来源支持定时拉取配置");
        }
        if (!StringUtils.hasText(meta.getScheduleCron())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "启用定时拉取时必须填写 scheduleCron");
        }
    }

    private String normalizeChunkConfigJson(ChunkingMode mode, String chunkConfigJson) {
        if (!StringUtils.hasText(chunkConfigJson)) {
            return null;
        }
        try {
            objectMapper.readValue(chunkConfigJson.trim(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分块参数 JSON 格式不合法");
        }
        return chunkConfigJson.trim();
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

    private List<List<Float>> embedBatch(List<String> texts, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embedBatch(texts, model)
                : embeddingService.embedBatch(texts);
    }

    private static float[] toPrimitive(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private void savePermissionRows(Long documentId, DocumentPermissionType type, KbDocumentUploadRequest meta, Long createdBy) {
        if (type == DocumentPermissionType.USER && meta.getGrantUserIds() != null) {
            for (Long uid : meta.getGrantUserIds()) {
                KbDocumentPermission row = new KbDocumentPermission();
                row.setDocumentId(documentId);
                row.setPermissionTargetType("USER");
                row.setPermissionTargetId(uid);
                row.setPermissionLevel("READ");
                row.setCreatedBy(createdBy);
                row.setCreatedAt(LocalDateTime.now());
                kbDocumentPermissionMapper.insert(row);
            }
        }
        if (type == DocumentPermissionType.PROJECT && meta.getGrantProjectId() != null) {
            KbDocumentPermission row = new KbDocumentPermission();
            row.setDocumentId(documentId);
            row.setPermissionTargetType("PROJECT");
            row.setPermissionTargetId(meta.getGrantProjectId());
            row.setPermissionLevel("READ");
            row.setCreatedBy(createdBy);
            row.setCreatedAt(LocalDateTime.now());
            kbDocumentPermissionMapper.insert(row);
        }
    }
}
