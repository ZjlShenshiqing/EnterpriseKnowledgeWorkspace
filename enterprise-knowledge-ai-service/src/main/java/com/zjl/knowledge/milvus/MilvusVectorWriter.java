package com.zjl.knowledge.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Milvus 向量写入/删除，字段与删除表达式对齐参考 {@code MilvusVectorStoreService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorWriter {

    private static final Gson GSON = new Gson();

    private static final int CONTENT_MAX_LEN = 65535;

    private final MilvusClientV2 milvusClient;

    private final MilvusProperties milvusProperties;

    private String resolveCollection(String collectionName) {
        return StringUtils.hasText(collectionName) ? collectionName : milvusProperties.getCollection();
    }

    private String resolvedCollectionNameForMetadata(String collectionName) {
        return resolveCollection(collectionName);
    }

    /**
     * 批量写入文档切片向量（单请求插入多行）。
     */
    public void indexDocumentChunks(String collectionName, String docId, List<VectorDocChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档分块不允许为空");
        }
        final int dim = milvusProperties.getVectorDimension();
        String col = resolveCollection(collectionName);
        String metaCollection = resolvedCollectionNameForMetadata(collectionName);
        try {
            List<JsonObject> rows = new ArrayList<>(chunks.size());
            for (VectorDocChunk chunk : chunks) {
                if (!StringUtils.hasText(chunk.getChunkId())) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 主键不能为空");
                }
                float[] vector = requireVector(chunk, dim);
                String content = chunk.getContent() == null ? "" : chunk.getContent();
                if (content.length() > CONTENT_MAX_LEN) {
                    content = content.substring(0, CONTENT_MAX_LEN);
                }
                JsonObject metadata = buildMetadata(metaCollection, docId, chunk);
                JsonObject row = new JsonObject();
                row.addProperty("id", chunk.getChunkId());
                row.addProperty("content", content);
                row.add("metadata", metadata);
                row.add("embedding", toJsonArray(vector));
                rows.add(row);
            }
            InsertReq req = InsertReq.builder()
                    .collectionName(col)
                    .data(rows)
                    .build();
            InsertResp resp = milvusClient.insert(req);
            log.info("Milvus chunk 写入成功, collection={}, insertCnt={}", col, resp.getInsertCnt());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    ErrorCode.VECTOR_WRITE_FAILED.getMessage() + ": " + ex.getMessage());
        }
    }

    /**
     * 更新单条切片（Upsert，与参考一致）。
     */
    public void upsertChunk(String collectionName, String docId, VectorDocChunk chunk) {
        if (chunk == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 不能为空");
        }
        final int dim = milvusProperties.getVectorDimension();
        String col = resolveCollection(collectionName);
        String metaCollection = resolvedCollectionNameForMetadata(collectionName);
        String chunkPk = StringUtils.hasText(chunk.getChunkId()) ? chunk.getChunkId() : null;
        if (!StringUtils.hasText(chunkPk)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Chunk 主键不能为空");
        }
        try {
            float[] vector = requireVector(chunk, dim);
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            if (content.length() > CONTENT_MAX_LEN) {
                content = content.substring(0, CONTENT_MAX_LEN);
            }
            JsonObject metadata = buildMetadata(metaCollection, docId, chunk);
            JsonObject row = new JsonObject();
            row.addProperty("id", chunkPk);
            row.addProperty("content", content);
            row.add("metadata", metadata);
            row.add("embedding", toJsonArray(vector));

            UpsertReq upsertReq = UpsertReq.builder()
                    .collectionName(col)
                    .data(List.of(row))
                    .build();
            UpsertResp resp = milvusClient.upsert(upsertReq);
            log.info("Milvus 更新 chunk 成功, collection={}, docId={}, chunkId={}, upsertCnt={}",
                    col, docId, chunkPk, resp.getUpsertCnt());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库更新失败: " + ex.getMessage());
        }
    }

    /**
     * 按文档删除全部向量：{@code metadata["doc_id"] == "..."}。
     */
    public void deleteByDocumentId(String collectionName, String docId) {
        try {
            String filter = "metadata[\"doc_id\"] == \"" + escapeFilterString(docId) + "\"";
            DeleteReq req = DeleteReq.builder()
                    .collectionName(resolveCollection(collectionName))
                    .filter(filter)
                    .build();
            DeleteResp resp = milvusClient.delete(req);
            log.info("Milvus 按文档删除向量, collection={}, docId={}, deleteCnt={}",
                    resolveCollection(collectionName), docId, resp.getDeleteCnt());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库删除失败: " + ex.getMessage());
        }
    }

    /**
     * 按主键 {@code id} 删除单条。
     */
    public void deleteByChunkId(String collectionName, String chunkId) {
        try {
            String filter = "id == \"" + escapeFilterString(chunkId) + "\"";
            DeleteReq req = DeleteReq.builder()
                    .collectionName(resolveCollection(collectionName))
                    .filter(filter)
                    .build();
            DeleteResp resp = milvusClient.delete(req);
            log.info("Milvus 按 chunk 删除向量, collection={}, chunkId={}, deleteCnt={}",
                    resolveCollection(collectionName), chunkId, resp.getDeleteCnt());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库删除失败: " + ex.getMessage());
        }
    }

    /**
     * 批量按主键 {@code id in [...]} 删除。
     */
    public void deleteByChunkIds(String collectionName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        try {
            String idList = chunkIds.stream()
                    .map(id -> "\"" + escapeFilterString(id) + "\"")
                    .collect(Collectors.joining(", "));
            String filter = "id in [" + idList + "]";
            DeleteReq req = DeleteReq.builder()
                    .collectionName(resolveCollection(collectionName))
                    .filter(filter)
                    .build();
            DeleteResp resp = milvusClient.delete(req);
            log.info("Milvus 批量删除 chunk 向量, collection={}, count={}, deleteCnt={}",
                    resolveCollection(collectionName), chunkIds.size(), resp.getDeleteCnt());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库删除失败: " + ex.getMessage());
        }
    }

    private static String escapeFilterString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static float[] requireVector(VectorDocChunk chunk, int expectedDim) {
        float[] vector = chunk.getEmbedding();
        if (vector == null || vector.length == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "向量不能为空");
        }
        if (vector.length != expectedDim) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "向量维度不匹配，期望 " + expectedDim + "，实际 " + vector.length);
        }
        return vector;
    }

    private static JsonArray toJsonArray(float[] v) {
        JsonArray arr = new JsonArray(v.length);
        for (float x : v) {
            arr.add(x);
        }
        return arr;
    }

    private JsonObject buildMetadata(String collectionNameForMeta, String docId, VectorDocChunk chunk) {
        JsonObject metadata = new JsonObject();
        Map<String, Object> extra = chunk.getMetadata();
        if (extra != null) {
            extra.forEach((k, v) -> metadata.add(k, GSON.toJsonTree(v)));
        }
        metadata.addProperty("collection_name", collectionNameForMeta);
        metadata.addProperty("doc_id", docId);
        metadata.addProperty("chunk_index", chunk.getIndex());
        return metadata;
    }
}
