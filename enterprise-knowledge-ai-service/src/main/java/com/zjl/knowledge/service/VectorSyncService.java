package com.zjl.knowledge.service;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.embedding.EmbeddingService;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.milvus.ChunkVectorStore;
import com.zjl.knowledge.milvus.VectorDocChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量同步服务：统一管理嵌入生成与 Milvus 读写，供所有业务服务复用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSyncService {

    private final EmbeddingService embeddingService;
    private final ChunkVectorStore chunkVectorStore;
    private final KbMilvusRoutingService kbMilvusRoutingService;

    /**
     * 单文本 → 向量列表
     */
    public List<Float> embed(String text, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embed(text, model)
                : embeddingService.embed(text);
    }

    /**
     * 批量文本 → 向量列表
     */
    public List<List<Float>> embedBatch(List<String> texts, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embedBatch(texts, model)
                : embeddingService.embedBatch(texts);
    }

    /**
     * 是否应该对该文档执行向量化
     */
    public boolean shouldEmbed(KbDocument document) {
        return kbMilvusRoutingService.shouldEmbed(document);
    }

    /**
     * 解析向量写入集合名
     */
    public String resolveCollection(KbDocument document) {
        return kbMilvusRoutingService.collectionForVectorWrite(document);
    }

    /**
     * 解析向量写入集合名（回退默认集合）
     */
    public String resolveCollectionOrDefault(KbDocument document) {
        return kbMilvusRoutingService.collectionForVectorWriteOrDefault(document);
    }

    /**
     * 将单个 Chunk 转为带向量的 VectorDocChunk 并写入 Milvus
     */
    public void syncChunk(KbDocument document, KbDocumentChunk chunk) {
        float[] vector = toArray(embed(chunk.getChunkText(), document));
        VectorDocChunk vc = VectorDocChunk.builder()
                .chunkId(String.valueOf(chunk.getId()))
                .content(chunk.getChunkText())
                .index(chunk.getChunkIndex())
                .embedding(vector)
                .build();
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), List.of(vc));
    }

    /**
     * 为 Chunk 列表附加向量后批量写入 Milvus
     */
    public void syncChunks(KbDocument document, List<KbDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<VectorDocChunk> vectorChunks = buildVectorChunks(document, chunks);
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), vectorChunks);
    }

    /**
     * 更新单条 Chunk 向量（Upsert）
     */
    public void updateChunk(KbDocument document, KbDocumentChunk chunk) {
        float[] vector = toArray(embed(chunk.getChunkText(), document));
        String collection = resolveCollection(document);
        chunkVectorStore.updateChunk(collection, document.getId(),
                VectorDocChunk.builder()
                        .chunkId(String.valueOf(chunk.getId()))
                        .content(chunk.getChunkText())
                        .index(chunk.getChunkIndex())
                        .embedding(vector)
                        .build());
    }

    /**
     * 直接写入已构建好的 VectorDocChunk 列表（用于分块流程中向量已预计算好的场景）
     */
    public void indexDocumentChunks(KbDocument document, List<VectorDocChunk> vectorChunks) {
        if (vectorChunks == null || vectorChunks.isEmpty()) {
            return;
        }
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), vectorChunks);
    }

    /**
     * 删除文档所有向量
     */
    public void deleteDocumentVectors(KbDocument document) {
        String collection = resolveCollectionOrDefault(document);
        chunkVectorStore.deleteDocumentVectors(collection, document.getId());
    }

    /**
     * 删除单条 Chunk 向量
     */
    public void deleteChunkVector(KbDocument document, String chunkId) {
        String collection = resolveCollection(document);
        chunkVectorStore.deleteChunkById(collection, chunkId);
    }

    /**
     * 批量删除 Chunk 向量
     */
    public void deleteChunkVectors(KbDocument document, List<String> chunkIds) {
        String collection = resolveCollection(document);
        chunkVectorStore.deleteChunksByIds(collection, chunkIds);
    }

    /**
     * 将 Chunk 实体列表转为带向量的 VectorDocChunk 列表
     */
    private List<VectorDocChunk> buildVectorChunks(KbDocument document, List<KbDocumentChunk> chunks) {
        List<String> texts = chunks.stream().map(KbDocumentChunk::getChunkText).collect(Collectors.toList());
        List<List<Float>> vectors = embedBatch(texts, document);
        if (vectors.size() != chunks.size()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "向量结果数量与 Chunk 数不一致");
        }
        List<VectorDocChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KbDocumentChunk c = chunks.get(i);
            result.add(VectorDocChunk.builder()
                    .chunkId(String.valueOf(c.getId()))
                    .content(c.getChunkText())
                    .index(c.getChunkIndex())
                    .embedding(toArray(vectors.get(i)))
                    .build());
        }
        return result;
    }

    /**
     * List&lt;Float&gt; → float[]
     */
    public static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
