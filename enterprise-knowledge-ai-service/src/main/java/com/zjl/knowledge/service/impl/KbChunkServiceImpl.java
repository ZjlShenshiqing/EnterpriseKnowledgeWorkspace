package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.dto.chunk.KbChunkBatchRequest;
import com.zjl.knowledge.dto.chunk.KbChunkCreateRequest;
import com.zjl.knowledge.dto.chunk.KbChunkPageRequest;
import com.zjl.knowledge.dto.chunk.KbChunkUpdateRequest;
import com.zjl.knowledge.dto.chunk.KbChunkVO;
import com.zjl.knowledge.embedding.EmbeddingService;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.milvus.ChunkVectorStore;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.KbChunkService;
import com.zjl.knowledge.service.KbMilvusRoutingService;
import com.zjl.knowledge.token.TokenCounterService;
import com.zjl.knowledge.util.ContentHashUtil;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识库 Chunk 服务实现（行为对齐参考：分页、增删改、批量、启用/禁用与向量同步）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbChunkServiceImpl implements KbChunkService {

    private final KbDocumentChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final KbDocumentPermissionMapper permissionMapper;
    private final EmbeddingService embeddingService;
    private final TokenCounterService tokenCounterService;
    private final ChunkVectorStore chunkVectorStore;
    private final TransactionTemplate transactionTemplate;
    private final DocumentVisibilityService documentVisibilityService;

    private final KbMilvusRoutingService kbMilvusRoutingService;

    @Override
    public IPage<KbChunkVO> pageQuery(Long docId, KbChunkPageRequest requestParam, UserContext user) {
        KbDocument document = loadDocOrThrow(docId);
        assertReadable(document, user);

        LambdaQueryWrapper<KbDocumentChunk> queryWrapper = new LambdaQueryWrapper<KbDocumentChunk>()
                .eq(KbDocumentChunk::getDocumentId, docId)
                .eq(requestParam.getEnabled() != null, KbDocumentChunk::getEnabled, requestParam.getEnabled())
                .orderByAsc(KbDocumentChunk::getChunkIndex);

        Page<KbDocumentChunk> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KbDocumentChunk> result = chunkMapper.selectPage(page, queryWrapper);
        return result.convert(this::toVo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbChunkVO create(Long docId, KbChunkCreateRequest requestParam, UserContext user) {
        KbDocument document = loadDocOrThrow(docId);
        assertWritable(document, user);
        assertDocNotBusy(document);
        assertDocumentEnabledForMutation(document);

        String content = requestParam.getContent();
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 内容不能为空");
        }

        KbDocumentChunk latest = chunkMapper.selectOne(
                Wrappers.lambdaQuery(KbDocumentChunk.class)
                        .eq(KbDocumentChunk::getDocumentId, docId)
                        .orderByDesc(KbDocumentChunk::getChunkIndex)
                        .last("LIMIT 1")
        );
        int chunkIndex = requestParam.getIndex() != null
                ? requestParam.getIndex()
                : (latest != null && latest.getChunkIndex() != null ? latest.getChunkIndex() + 1 : 0);

        long chunkPk = requestParam.getChunkId() != null ? requestParam.getChunkId() : IdWorker.getId();
        String vectorId = String.valueOf(chunkPk);
        int charCount = content.length();
        Integer tokenCount = resolveTokenCount(content);
        Long uid = user.getUserId();

        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(chunkPk);
        chunk.setDocumentId(docId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(content);
        chunk.setContentHash(ContentHashUtil.sha256Hex(content));
        chunk.setCharCount(charCount);
        chunk.setTokenCount(tokenCount);
        chunk.setEnabled(1);
        chunk.setVectorId(vectorId);
        chunk.setMetadataJson("{}");
        chunk.setCreatedBy(uid);
        chunk.setUpdatedBy(uid);
        chunk.setCreatedAt(LocalDateTime.now());
        chunk.setUpdatedAt(LocalDateTime.now());

        chunkMapper.insert(chunk);
        log.info("新增 Chunk 成功, docId={}, chunkId={}, chunkIndex={}", docId, chunk.getId(), chunkIndex);

        documentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, docId)
                .setSql("chunk_count = chunk_count + 1"));

        syncChunkToVector(document, chunk);
        return toVo(chunk);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(Long docId, List<KbChunkCreateRequest> requestParams, UserContext user) {
        batchCreate(docId, requestParams, false, user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(Long docId, List<KbChunkCreateRequest> requestParams, boolean writeVector, UserContext user) {
        if (requestParams == null || requestParams.isEmpty()) {
            return;
        }

        KbDocument document = loadDocOrThrow(docId);
        assertWritable(document, user);
        assertDocNotBusy(document);
        assertDocumentEnabledForMutation(document);

        boolean needAutoIndex = requestParams.stream().anyMatch(r -> r.getIndex() == null);
        int nextIndex = 0;
        if (needAutoIndex) {
            KbDocumentChunk latest = chunkMapper.selectOne(
                    new LambdaQueryWrapper<KbDocumentChunk>()
                            .eq(KbDocumentChunk::getDocumentId, docId)
                            .orderByDesc(KbDocumentChunk::getChunkIndex)
                            .last("LIMIT 1")
            );
            nextIndex = latest != null && latest.getChunkIndex() != null ? latest.getChunkIndex() + 1 : 0;
        }

        Long uid = user.getUserId();
        List<KbDocumentChunk> chunkList = new ArrayList<>(requestParams.size());

        for (KbChunkCreateRequest request : requestParams) {
            String content = request.getContent();
            if (!StringUtils.hasText(content)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 内容不能为空");
            }
            Integer chunkIndex = request.getIndex();
            if (chunkIndex == null) {
                chunkIndex = nextIndex++;
            }
            long chunkPk = request.getChunkId() != null ? request.getChunkId() : IdWorker.getId();
            String vectorId = String.valueOf(chunkPk);

            KbDocumentChunk chunk = new KbDocumentChunk();
            chunk.setId(chunkPk);
            chunk.setDocumentId(docId);
            chunk.setChunkIndex(chunkIndex);
            chunk.setChunkText(content);
            chunk.setContentHash(ContentHashUtil.sha256Hex(content));
            chunk.setCharCount(content.length());
            chunk.setTokenCount(resolveTokenCount(content));
            chunk.setEnabled(1);
            chunk.setVectorId(vectorId);
            chunk.setMetadataJson("{}");
            chunk.setCreatedBy(uid);
            chunk.setUpdatedBy(uid);
            chunk.setCreatedAt(LocalDateTime.now());
            chunk.setUpdatedAt(LocalDateTime.now());
            chunkList.add(chunk);
        }

        for (KbDocumentChunk c : chunkList) {
            chunkMapper.insert(c);
        }

        documentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, docId)
                .setSql("chunk_count = chunk_count + " + chunkList.size()));

        if (writeVector) {
            String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
            List<VectorDocChunk> vectorChunks = chunkList.stream()
                    .map(each -> VectorDocChunk.builder()
                            .chunkId(String.valueOf(each.getId()))
                            .content(each.getChunkText())
                            .index(each.getChunkIndex())
                            .build())
                    .collect(Collectors.toList());
            if (!vectorChunks.isEmpty()) {
                attachEmbeddings(vectorChunks, document);
                chunkVectorStore.indexDocumentChunks(milvusCollection, docId, vectorChunks);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long docId, Long chunkId, KbChunkUpdateRequest requestParam, UserContext user) {
        KbDocument document = loadDocOrThrow(docId);
        assertWritable(document, user);
        assertDocNotBusy(document);

        KbDocumentChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Chunk 不存在");
        }
        if (!chunk.getDocumentId().equals(docId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 不属于该文档");
        }

        String newContent = requestParam.getContent();
        if (!StringUtils.hasText(newContent)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 内容不能为空");
        }
        if (newContent.equals(chunk.getChunkText())) {
            return;
        }

        chunk.setChunkText(newContent);
        chunk.setContentHash(ContentHashUtil.sha256Hex(newContent));
        chunk.setCharCount(newContent.length());
        chunk.setTokenCount(resolveTokenCount(newContent));
        chunk.setUpdatedBy(user.getUserId());
        chunk.setUpdatedAt(LocalDateTime.now());
        chunkMapper.updateById(chunk);

        log.info("更新 Chunk 成功, docId={}, chunkId={}", docId, chunkId);

        String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
        float[] embedding = toArray(embedContent(newContent, kbMilvusRoutingService.embeddingModelOrDefault(document)));
        chunkVectorStore.updateChunk(
                milvusCollection,
                docId,
                VectorDocChunk.builder()
                        .chunkId(String.valueOf(chunkId))
                        .content(newContent)
                        .index(chunk.getChunkIndex())
                        .embedding(embedding)
                        .build()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long docId, Long chunkId, UserContext user) {
        KbDocument document = loadDocOrThrow(docId);
        assertWritable(document, user);
        assertDocNotBusy(document);

        KbDocumentChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Chunk 不存在");
        }
        if (!chunk.getDocumentId().equals(docId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 不属于该文档");
        }

        chunkMapper.deleteById(chunkId);

        documentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, docId)
                .setSql("chunk_count = CASE WHEN chunk_count > 0 THEN chunk_count - 1 ELSE 0 END"));

        log.info("删除 Chunk 成功, docId={}, chunkId={}", docId, chunkId);
        String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
        chunkVectorStore.deleteChunkById(milvusCollection, String.valueOf(chunkId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableChunk(Long docId, Long chunkId, boolean enabled, UserContext user) {
        KbDocument document = loadDocOrThrow(docId);
        assertWritable(document, user);
        assertDocNotBusy(document);
        validateDocumentEnabledForChunkEnable(document, enabled);

        KbDocumentChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Chunk 不存在");
        }
        if (!chunk.getDocumentId().equals(docId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 不属于该文档");
        }

        int enabledValue = enabled ? 1 : 0;
        if (Objects.equals(chunk.getEnabled(), enabledValue)) {
            return;
        }

        chunk.setEnabled(enabledValue);
        chunk.setUpdatedBy(user.getUserId());
        chunk.setUpdatedAt(LocalDateTime.now());
        chunkMapper.updateById(chunk);

        log.info("{}Chunk 成功, docId={}, chunkId={}", enabled ? "启用" : "禁用", docId, chunkId);

        if (enabled) {
            syncChunkToVector(document, chunk);
        } else {
            String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
            chunkVectorStore.deleteChunkById(milvusCollection, String.valueOf(chunkId));
        }
    }

    @Override
    public void batchToggleEnabled(Long docId, KbChunkBatchRequest requestParam, boolean enabled, UserContext user) {
        if (requestParam == null || requestParam.getChunkIds() == null || requestParam.getChunkIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定需要操作的 Chunk，全量启用/禁用请使用文档启用接口");
        }
        List<Long> requestedIds = requestParam.getChunkIds();
        if (requestedIds.size() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单次批量操作 Chunk 数量不能超过 500");
        }

        KbDocument document = loadDocOrThrow(docId);
        assertWritable(document, user);
        assertDocNotBusy(document);
        validateDocumentEnabledForChunkEnable(document, enabled);

        List<KbDocumentChunk> found = chunkMapper.selectList(
                new LambdaQueryWrapper<KbDocumentChunk>().in(KbDocumentChunk::getId, requestedIds)
        );
        if (found.size() != requestedIds.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "存在无效的 Chunk ID，请求 " + requestedIds.size() + " 个，实际找到 " + found.size() + " 个");
        }
        for (KbDocumentChunk c : found) {
            if (!c.getDocumentId().equals(docId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "Chunk " + c.getId() + " 不属于文档 " + docId);
            }
        }
        List<Long> targetIds = found.stream().map(KbDocumentChunk::getId).collect(Collectors.toList());
        if (targetIds.isEmpty()) {
            return;
        }

        int enabledValue = enabled ? 1 : 0;
        List<KbDocumentChunk> needUpdateChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbDocumentChunk>()
                        .in(KbDocumentChunk::getId, targetIds)
                        .ne(KbDocumentChunk::getEnabled, enabledValue)
        );
        List<Long> needUpdateIds = needUpdateChunks.stream().map(KbDocumentChunk::getId).collect(Collectors.toList());

        if (needUpdateIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    enabled ? "所有 Chunk 已全部启用，无需重复操作" : "所有 Chunk 已全部禁用，无需重复操作");
        }

        Long uid = user.getUserId();
        String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
        if (enabled) {
            List<VectorDocChunk> vectorChunks = needUpdateChunks.stream()
                    .map(c -> VectorDocChunk.builder()
                            .chunkId(String.valueOf(c.getId()))
                            .content(c.getChunkText())
                            .index(c.getChunkIndex())
                            .build())
                    .collect(Collectors.toList());
            attachEmbeddings(vectorChunks, document);

            transactionTemplate.executeWithoutResult(status -> {
                chunkMapper.update(null,
                        Wrappers.lambdaUpdate(KbDocumentChunk.class)
                                .in(KbDocumentChunk::getId, needUpdateIds)
                                .set(KbDocumentChunk::getEnabled, 1)
                                .set(KbDocumentChunk::getUpdatedBy, uid)
                                .set(KbDocumentChunk::getUpdatedAt, LocalDateTime.now())
                );
                chunkVectorStore.indexDocumentChunks(milvusCollection, docId, vectorChunks);
            });
        } else {
            List<String> idStrs = needUpdateIds.stream().map(String::valueOf).collect(Collectors.toList());
            transactionTemplate.executeWithoutResult(status -> {
                chunkMapper.update(null,
                        Wrappers.lambdaUpdate(KbDocumentChunk.class)
                                .in(KbDocumentChunk::getId, needUpdateIds)
                                .set(KbDocumentChunk::getEnabled, 0)
                                .set(KbDocumentChunk::getUpdatedBy, uid)
                                .set(KbDocumentChunk::getUpdatedAt, LocalDateTime.now())
                );
                chunkVectorStore.deleteChunksByIds(milvusCollection, idStrs);
            });
        }

        log.info("批量{}Chunk 成功, docId={}, count={}", enabled ? "启用" : "禁用", docId, needUpdateIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabledByDocId(Long docId, boolean enabled, Long updatedBy) {
        int enabledValue = enabled ? 1 : 0;
        chunkMapper.update(null,
                Wrappers.lambdaUpdate(KbDocumentChunk.class)
                        .eq(KbDocumentChunk::getDocumentId, docId)
                        .set(KbDocumentChunk::getEnabled, enabledValue)
                        .set(KbDocumentChunk::getUpdatedBy, updatedBy)
                        .set(KbDocumentChunk::getUpdatedAt, LocalDateTime.now())
        );
        log.info("根据文档ID更新所有Chunk启用状态, docId={}, enabled={}", docId, enabled);
    }

    @Override
    public List<KbChunkVO> listByDocId(Long docId, UserContext user) {
        KbDocument document = loadDocOrThrow(docId);
        assertReadable(document, user);

        List<KbDocumentChunk> list = chunkMapper.selectList(
                Wrappers.lambdaQuery(KbDocumentChunk.class)
                        .eq(KbDocumentChunk::getDocumentId, docId)
                        .orderByAsc(KbDocumentChunk::getChunkIndex)
        );
        return list.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocId(Long docId) {
        if (docId == null) {
            return;
        }
        chunkMapper.delete(new LambdaQueryWrapper<KbDocumentChunk>().eq(KbDocumentChunk::getDocumentId, docId));
    }

    private KbDocument loadDocOrThrow(Long docId) {
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private void assertReadable(KbDocument doc, UserContext user) {
        List<KbDocumentPermission> perms = permissionMapper.selectList(
                new LambdaQueryWrapper<KbDocumentPermission>().eq(KbDocumentPermission::getDocumentId, doc.getId())
        );
        if (!documentVisibilityService.canView(doc, user, perms)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void assertWritable(KbDocument doc, UserContext user) {
        assertReadable(doc, user);
        if (!user.isAdmin() && !Objects.equals(doc.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void assertDocNotBusy(KbDocument doc) {
        String s = doc.getStatus();
        if (DocumentStatus.PARSING.name().equals(s) || DocumentStatus.RUNNING.name().equals(s)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块处理中，暂不支持该操作");
        }
    }

    private void assertDocumentEnabledForMutation(KbDocument document) {
        if (document.getEnabled() != null && document.getEnabled() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档未启用，暂不支持新增 Chunk");
        }
    }

    private void validateDocumentEnabledForChunkEnable(KbDocument document, boolean enableChunk) {
        if (!enableChunk) {
            return;
        }
        if (document.getEnabled() != null && document.getEnabled() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档未启用，无法启用Chunk，请先启用文档");
        }
    }

    private void syncChunkToVector(KbDocument document, KbDocumentChunk chunk) {
        String embeddingModel = kbMilvusRoutingService.embeddingModelOrDefault(document);
        List<Float> embedding = embedContent(chunk.getChunkText(), embeddingModel);
        float[] vector = toArray(embedding);
        VectorDocChunk vc = VectorDocChunk.builder()
                .index(chunk.getChunkIndex())
                .content(chunk.getChunkText())
                .chunkId(String.valueOf(chunk.getId()))
                .embedding(vector)
                .build();
        String milvusCollection = kbMilvusRoutingService.collectionForVectorWrite(document);
        chunkVectorStore.indexDocumentChunks(milvusCollection, document.getId(), List.of(vc));
        log.debug("同步 Chunk 到向量库成功, docId={}, chunkId={}", document.getId(), chunk.getId());
    }

    private void attachEmbeddings(List<VectorDocChunk> chunks, KbDocument document) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String embeddingModel = kbMilvusRoutingService.embeddingModelOrDefault(document);
        List<String> texts = chunks.stream().map(VectorDocChunk::getContent).collect(Collectors.toList());
        List<List<Float>> vectors = embedBatch(texts, embeddingModel);
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "向量结果数量不匹配");
        }
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(toArray(vectors.get(i)));
        }
    }

    private List<Float> embedContent(String content, String embeddingModel) {
        return StringUtils.hasText(embeddingModel)
                ? embeddingService.embed(content, embeddingModel)
                : embeddingService.embed(content);
    }

    private List<List<Float>> embedBatch(List<String> texts, String embeddingModel) {
        return StringUtils.hasText(embeddingModel)
                ? embeddingService.embedBatch(texts, embeddingModel)
                : embeddingService.embedBatch(texts);
    }

    private Integer resolveTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return tokenCounterService.countTokens(content);
    }

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private KbChunkVO toVo(KbDocumentChunk e) {
        KbChunkVO vo = new KbChunkVO();
        vo.setId(e.getId());
        vo.setDocumentId(e.getDocumentId());
        vo.setChunkIndex(e.getChunkIndex());
        vo.setContent(e.getChunkText());
        vo.setContentHash(e.getContentHash());
        vo.setCharCount(e.getCharCount());
        vo.setTokenCount(e.getTokenCount());
        vo.setEnabled(e.getEnabled());
        vo.setVectorId(e.getVectorId());
        vo.setMetadataJson(e.getMetadataJson());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setUpdatedBy(e.getUpdatedBy());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setUpdatedAt(e.getUpdatedAt());
        return vo;
    }
}
