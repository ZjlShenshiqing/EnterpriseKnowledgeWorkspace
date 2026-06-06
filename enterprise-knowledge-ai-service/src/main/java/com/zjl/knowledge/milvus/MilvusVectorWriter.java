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
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Milvus 向量底层写入器
 *
 * <p>直接操作 {@link MilvusClientV2}，负责 Insert / Upsert / Delete 的
 * 请求构建与执行，包含向量维度校验和 content 超长截断</p>
 *
 * <p>删除操作使用标量过滤表达式：
 * 按文档删 {@code metadata["doc_id"] == "..."}；
 * 按切片删 {@code id == "..."} 或 {@code id in [...]}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorWriter {

    /**
     * Gson 实例，用于构建 metadata 和 embedding 的 JSON 结构
     */
    private static final Gson GSON = new Gson();

    /**
     * Milvus VarChar 字段最大长度，超出的 content 会被截断
     */
    private static final int CONTENT_MAX_LEN = 65535;

    /**
     * Milvus gRPC 客户端
     */
    private final MilvusClientV2 milvusClient;

    /**
     * Milvus 配置属性
     */
    private final MilvusProperties milvusProperties;

    /**
     * 解析集合名：传入空则回退到默认集合 {@code app.milvus.collection}
     *
     * @param collectionName 传入的集合名（可为空）
     * @return 实际使用的集合名
     */
    private String resolveCollection(String collectionName) {
        return StringUtils.hasText(collectionName) ? collectionName : milvusProperties.getCollection();
    }

    /**
     * 获取写入 metadata 时记录的集合名标识
     *
     * @param collectionName 传入的集合名
     * @return 实际集合名
     */
    private String resolvedCollectionNameForMetadata(String collectionName) {
        return resolveCollection(collectionName);
    }

    /**
     * 批量写入文档切片向量
     *
     * <p>单次 Insert 请求包含多行，每行含 id / content / metadata(JSON) / embedding 四个字段
     * content 超 {@value #CONTENT_MAX_LEN} 字符时自动截断</p>
     *
     * @param collectionName Milvus 集合名
     * @param docId          文档 ID 字符串
     * @param chunks         待写入的切片列表
     * @throws BizException 向量为空、维度不匹配或 Milvus 操作失败时抛出
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
            log.error("Milvus chunk 写入失败: collection={}, docId={}", col, docId, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    ErrorCode.VECTOR_WRITE_FAILED.getMessage() + ": " + ex.getMessage());
        }
    }

    /**
     * 更新单条切片向量（Upsert）
     *
     * <p>同一主键的行会被覆盖，实现单条切片的向量更新
     * content 超长时同样会截断</p>
     *
     * @param collectionName Milvus 集合名
     * @param docId          文档 ID 字符串
     * @param chunk          待更新的切片
     * @throws BizException 切片为空、主键缺失、向量校验失败或 Milvus 操作失败时抛出
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
            log.error("Milvus 更新 chunk 失败: collection={}, docId={}, chunkId={}", col, docId, chunkPk, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库更新失败: " + ex.getMessage());
        }
    }

    /**
     * 按文档 ID 删除该文档所有向量
     *
     * <p>使用标量过滤表达式 {@code metadata["doc_id"] == "docId"}，
     * 删除前会转义双引号和反斜杠防止注入</p>
     *
     * @param collectionName Milvus 集合名
     * @param docId          文档 ID 字符串
     * @throws BizException Milvus 操作失败时抛出
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
            log.error("Milvus 按文档删除向量失败: collection={}, docId={}", resolveCollection(collectionName), docId, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库删除失败: " + ex.getMessage());
        }
    }

    /**
     * 按切片主键删除单条向量
     *
     * <p>使用表达式 {@code id == "chunkId"}</p>
     *
     * @param collectionName Milvus 集合名
     * @param chunkId        切片主键字符串
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
            log.error("Milvus 按 chunk 删除向量失败: collection={}, chunkId={}", resolveCollection(collectionName), chunkId, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库删除失败: " + ex.getMessage());
        }
    }

    /**
     * 按切片主键列表批量删除向量
     *
     * <p>使用表达式 {@code id in ["chunkId1", "chunkId2", ...]}，
     * 空列表直接返回不执行操作</p>
     *
     * @param collectionName Milvus 集合名
     * @param chunkIds       切片主键字符串列表
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
            log.error("Milvus 批量删除 chunk 向量失败: collection={}", resolveCollection(collectionName), ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库删除失败: " + ex.getMessage());
        }
    }

    /**
     * 向量相似度检索
     *
     * @param collectionName Milvus 集合名
     * @param vector         查询向量
     * @param topK           返回数量
     * @param filter         标量过滤表达式（可选，如 metadata["doc_id"] in [...]）
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String collectionName, float[] vector, int topK, String filter) {
        try {
            List<Float> queryVector = new ArrayList<>(vector.length);
            for (float v : vector) {
                queryVector.add(v);
            }
            String col = resolveCollection(collectionName);
            SearchReq req = SearchReq.builder()
                    .collectionName(col)
                    .data(Collections.singletonList(new io.milvus.v2.service.vector.request.data.FloatVec(queryVector)))
                    .topK(topK)
                    .outputFields(List.of("id", "metadata"))
                    .filter(filter != null ? filter : "")
                    .build();
            SearchResp resp = milvusClient.search(req);

            List<SearchResult> results = new ArrayList<>();
            if (resp.getSearchResults() != null && !resp.getSearchResults().isEmpty()) {
                for (List<SearchResp.SearchResult> resultList : resp.getSearchResults()) {
                    for (SearchResp.SearchResult sr : resultList) {
                        String chunkId = (String) sr.getEntity().get("id");
                        Map<String, Object> metaObj = getMetadata(sr.getEntity());
                        String docId = metaObj != null ? (String) metaObj.get("doc_id") : null;
                        results.add(new SearchResult(chunkId, docId, sr.getScore(), metaObj));
                    }
                }
            }
            return results;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量检索失败: " + ex.getMessage());
        }
    }

    /**
     * Hybrid search：dense 向量 + sparse 向量双路检索，RRF 融合
     *
     * <p>分别执行 dense ANN (topK × multiplier) 和 sparse ANN (topK × multiplier)，
     * 按 chunkId 去重后通过 RRF 公式融合排序，返回最终 topK 结果</p>
     *
     * @param collectionName Milvus hybrid 集合名
     * @param denseVector    dense 查询向量
     * @param sparseVector   sparse 查询向量
     * @param topK           最终返回数量
     * @param filter         标量过滤表达式（可选）
     * @param rrfK           RRF k 参数
     * @param topNMultiplier 每路 ANN 候选扩张倍数
     * @return 融合后的搜索结果（按 RRF score 降序，最多 topK 条）
     */
    public List<SearchResult> hybridSearch(String collectionName,
                                           float[] denseVector,
                                           Map<Long, Float> sparseVector,
                                           int topK,
                                           String filter,
                                           int rrfK,
                                           int topNMultiplier) {
        try {
            int annTopK = topK * topNMultiplier;
            String col = resolveCollection(collectionName);

            List<SearchResult> denseResults = search(col, denseVector, annTopK, filter);
            List<SearchResult> sparseResults = sparseSearch(col, sparseVector, annTopK, filter);

            Map<String, Map<Integer, Float>> rrfScores = new java.util.LinkedHashMap<>();
            for (int i = 0; i < denseResults.size(); i++) {
                SearchResult r = denseResults.get(i);
                String key = keyOf(r);
                rrfScores.computeIfAbsent(key, k -> new java.util.HashMap<>())
                        .put(0, 1.0f / (rrfK + i + 1));
            }
            for (int i = 0; i < sparseResults.size(); i++) {
                SearchResult r = sparseResults.get(i);
                String key = keyOf(r);
                rrfScores.computeIfAbsent(key, k -> new java.util.HashMap<>())
                        .put(1, 1.0f / (rrfK + i + 1));
            }

            Map<String, SearchResult> bestPerKey = new java.util.LinkedHashMap<>();
            for (SearchResult r : denseResults) {
                bestPerKey.putIfAbsent(keyOf(r), r);
            }
            for (SearchResult r : sparseResults) {
                bestPerKey.putIfAbsent(keyOf(r), r);
            }

            return rrfScores.entrySet().stream()
                    .sorted((a, b) -> {
                        float sumB = b.getValue().values().stream().reduce(0f, Float::sum);
                        float sumA = a.getValue().values().stream().reduce(0f, Float::sum);
                        return Float.compare(sumB, sumA);
                    })
                    .limit(topK)
                    .map(e -> {
                        SearchResult base = bestPerKey.get(e.getKey());
                        float rrfScore = e.getValue().values().stream().reduce(0f, Float::sum);
                        return new SearchResult(
                                base != null ? base.chunkId() : null,
                                base != null ? base.docId() : null,
                                rrfScore,
                                base != null ? base.metadata() : Map.of());
                    })
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "Hybrid 检索失败: " + ex.getMessage());
        }
    }

    /**
     * 稀疏向量检索
     */
    private List<SearchResult> sparseSearch(String collectionName, Map<Long, Float> sparseVector, int topK, String filter) {
        try {
            String col = resolveCollection(collectionName);
            SortedMap<Long, Float> sorted = new java.util.TreeMap<>(sparseVector);
            SearchReq req = SearchReq.builder()
                    .collectionName(col)
                    .data(Collections.singletonList(new SparseFloatVec(sorted)))
                    .topK(topK)
                    .outputFields(List.of("id", "metadata"))
                    .filter(filter != null ? filter : "")
                    .build();
            SearchResp resp = milvusClient.search(req);

            List<SearchResult> results = new ArrayList<>();
            if (resp.getSearchResults() != null && !resp.getSearchResults().isEmpty()) {
                for (List<SearchResp.SearchResult> resultList : resp.getSearchResults()) {
                    for (SearchResp.SearchResult sr : resultList) {
                        String chunkId = (String) sr.getEntity().get("id");
                        Map<String, Object> metaObj = getMetadata(sr.getEntity());
                        String docId = metaObj != null ? (String) metaObj.get("doc_id") : null;
                        results.add(new SearchResult(chunkId, docId, sr.getScore(), metaObj));
                    }
                }
            }
            return results;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "稀疏向量检索失败: " + ex.getMessage());
        }
    }

    private static String keyOf(SearchResult r) {
        return r.chunkId() + "@" + r.docId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMetadata(Map<String, Object> entity) {
        Object meta = entity.get("metadata");
        if (meta instanceof Map) {
            return (Map<String, Object>) meta;
        }
        return null;
    }

    /**
     * 转义过滤表达式中的特殊字符，防止注入
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeFilterString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 校验向量非空且维度匹配
     *
     * @param chunk       切片载荷
     * @param expectedDim 期望维度
     * @return 校验通过的向量
     * @throws BizException 向量为空或维度不匹配时抛出
     */
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

    /**
     * 将 float 数组转为 Gson JsonArray
     *
     * @param v 向量数组
     * @return JsonArray
     */
    private static JsonArray toJsonArray(float[] v) {
        JsonArray arr = new JsonArray(v.length);
        for (float x : v) {
            arr.add(x);
        }
        return arr;
    }

    /**
     * 构建 Milvus metadata JSON 对象
     *
     * <p>固定包含 {@code collection_name}、{@code doc_id}、{@code chunk_index}，
     * 同时合并 {@link VectorDocChunk#getMetadata()} 中的业务扩展字段</p>
     *
     * @param collectionNameForMeta 集合名
     * @param docId                 文档 ID 字符串
     * @param chunk                 切片载荷
     * @return metadata JSON 对象
     */
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

    /**
     * 批量写入文档切片向量到 hybrid collection（含 dense + sparse 双向量）
     *
     * @param collectionName Milvus hybrid 集合名
     * @param docId          文档 ID 字符串
     * @param chunks         待写入的切片列表（必须含 embedding 和 sparseVector）
     */
    public void indexHybridChunks(String collectionName, String docId, List<VectorDocChunk> chunks) {
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
                Map<Long, Float> sparseVec = requireSparseVector(chunk);
                String content = chunk.getContent() == null ? "" : chunk.getContent();
                if (content.length() > CONTENT_MAX_LEN) {
                    content = content.substring(0, CONTENT_MAX_LEN);
                }
                JsonObject metadata = buildMetadata(metaCollection, docId, chunk);
                JsonObject row = new JsonObject();
                row.addProperty("id", chunk.getChunkId());
                row.addProperty("content", content);
                row.add("metadata", metadata);
                row.add("dense_vector", toJsonArray(vector));
                row.add("sparse_vector", toSparseJson(sparseVec));
                rows.add(row);
            }
            InsertReq req = InsertReq.builder()
                    .collectionName(col)
                    .data(rows)
                    .build();
            InsertResp resp = milvusClient.insert(req);
            log.info("Milvus hybrid chunk 写入成功, collection={}, insertCnt={}", col, resp.getInsertCnt());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Milvus hybrid chunk 写入失败: collection={}, docId={}", col, docId, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    ErrorCode.VECTOR_WRITE_FAILED.getMessage() + ": " + ex.getMessage());
        }
    }

    /**
     * 更新单条切片向量到 hybrid collection（Upsert，含 dense + sparse）
     *
     * @param collectionName Milvus hybrid 集合名
     * @param docId          文档 ID 字符串
     * @param chunk          待更新的切片（必须含 embedding 和 sparseVector）
     */
    public void upsertHybridChunk(String collectionName, String docId, VectorDocChunk chunk) {
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
            Map<Long, Float> sparseVec = requireSparseVector(chunk);
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            if (content.length() > CONTENT_MAX_LEN) {
                content = content.substring(0, CONTENT_MAX_LEN);
            }
            JsonObject metadata = buildMetadata(metaCollection, docId, chunk);
            JsonObject row = new JsonObject();
            row.addProperty("id", chunkPk);
            row.addProperty("content", content);
            row.add("metadata", metadata);
            row.add("dense_vector", toJsonArray(vector));
            row.add("sparse_vector", toSparseJson(sparseVec));

            UpsertReq upsertReq = UpsertReq.builder()
                    .collectionName(col)
                    .data(List.of(row))
                    .build();
            UpsertResp resp = milvusClient.upsert(upsertReq);
            log.info("Milvus hybrid 更新 chunk 成功, collection={}, docId={}, chunkId={}, upsertCnt={}",
                    col, docId, chunkPk, resp.getUpsertCnt());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Milvus hybrid 更新 chunk 失败: collection={}, docId={}, chunkId={}", col, docId, chunkPk, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "向量库更新失败: " + ex.getMessage());
        }
    }

    /**
     * 校验稀疏向量非空（hybrid 写入必需）
     *
     * @param chunk 切片载荷
     * @return 校验通过的稀疏向量
     * @throws BizException 稀疏向量为空时抛出
     */
    private static Map<Long, Float> requireSparseVector(VectorDocChunk chunk) {
        Map<Long, Float> sparseVec = chunk.getSparseVector();
        if (sparseVec == null || sparseVec.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "稀疏向量不能为空（hybrid 模式必需）");
        }
        return sparseVec;
    }

    /**
     * 将稀疏向量转为 Milvus JSON 对象（位置→权重）
     *
     * @param sparseVec 稀疏向量
     * @return Gson JsonObject
     */
    private static JsonObject toSparseJson(Map<Long, Float> sparseVec) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<Long, Float> entry : sparseVec.entrySet()) {
            obj.addProperty(String.valueOf(entry.getKey()), entry.getValue());
        }
        return obj;
    }
}
