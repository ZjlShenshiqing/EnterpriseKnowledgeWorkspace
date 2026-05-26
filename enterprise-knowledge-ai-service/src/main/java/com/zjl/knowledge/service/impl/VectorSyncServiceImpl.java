package com.zjl.knowledge.service.impl;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.embedding.EmbeddingService;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.milvus.ChunkVectorStore;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.service.KbMilvusRoutingService;
import com.zjl.knowledge.service.VectorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSyncServiceImpl implements VectorSyncService {

    private final EmbeddingService embeddingService;
    private final ChunkVectorStore chunkVectorStore;
    private final KbMilvusRoutingService kbMilvusRoutingService;

    @Override
    public List<Float> embed(String text, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embed(text, model)
                : embeddingService.embed(text);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embedBatch(texts, model)
                : embeddingService.embedBatch(texts);
    }

    @Override
    public boolean shouldEmbed(KbDocument document) {
        return kbMilvusRoutingService.shouldEmbed(document);
    }

    @Override
    public String resolveCollection(KbDocument document) {
        return kbMilvusRoutingService.collectionForVectorWrite(document);
    }

    @Override
    public String resolveCollectionOrDefault(KbDocument document) {
        return kbMilvusRoutingService.collectionForVectorWriteOrDefault(document);
    }

    @Override
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

    @Override
    public void syncChunks(KbDocument document, List<KbDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<VectorDocChunk> vectorChunks = buildVectorChunks(document, chunks);
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), vectorChunks);
    }

    @Override
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

    @Override
    public void indexDocumentChunks(KbDocument document, List<VectorDocChunk> vectorChunks) {
        if (vectorChunks == null || vectorChunks.isEmpty()) {
            return;
        }
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), vectorChunks);
    }

    @Override
    public void deleteDocumentVectors(KbDocument document) {
        String collection = resolveCollectionOrDefault(document);
        chunkVectorStore.deleteDocumentVectors(collection, document.getId());
    }

    @Override
    public void deleteChunkVector(KbDocument document, String chunkId) {
        String collection = resolveCollection(document);
        chunkVectorStore.deleteChunkById(collection, chunkId);
    }

    @Override
    public void deleteChunkVectors(KbDocument document, List<String> chunkIds) {
        String collection = resolveCollection(document);
        chunkVectorStore.deleteChunksByIds(collection, chunkIds);
    }

    @Override
    public List<com.zjl.knowledge.milvus.SearchResult> searchSimilar(String query, int topK, KbDocument document) {
        float[] vector = toArray(embed(query, document));
        String collection = resolveCollectionOrDefault(document);
        return chunkVectorStore.search(collection, vector, topK, null);
    }

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
}
