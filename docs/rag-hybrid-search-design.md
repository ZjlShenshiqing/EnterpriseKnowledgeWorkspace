# RAG Hybrid Search 方案说明

## 1. 背景

当前 RAG 检索主要依赖 Milvus 单路 dense vector 召回：

```text
用户问题
  -> embedding
  -> Milvus vector search
  -> DB 查询文档和 chunk
  -> 文档权限终检
  -> 返回 matchedChunks
```

这个链路适合语义相似问题，但对精确词、制度编号、项目名、人名、表格字段、缩写、编码类问题不稳定。例如：

1. “制度 3.2.1 怎么写”更依赖编号精确匹配。
2. “OA-2025-001”这类编码在 dense embedding 中区分度弱。
3. 人名、项目代号、字段名、表格列值更适合关键词/BM25。
4. 中文短 query 容易语义过泛，纯向量召回可能拿到相近但不是答案的 chunk。

因此需要增加 hybrid search：dense vector 负责语义召回，sparse/BM25 负责关键词召回，再通过融合排序输出候选。

## 2. 参考方案

公开资料里，各家主流 RAG/搜索产品的混合检索思路基本一致：

1. **Milvus**：支持 multi-vector hybrid search，一个 collection 可包含多个向量字段，例如 dense vector 和 sparse vector；查询时用多个 ANN 请求，并通过 ranker 融合。
2. **Azure AI Search**：hybrid search 同时执行 full-text query 和 vector query，并使用 RRF 合并结果。
3. **OpenSearch / AWS OpenSearch**：hybrid query 支持 lexical、semantic、neural 等子查询组合，并通过 search pipeline 做 normalization / score combination。
4. **Elastic**：支持 BM25 和 kNN/vector 结果融合，常见推荐是 RRF。
5. **Pinecone / Weaviate**：常见模式是 dense 向量 + sparse 向量或 BM25，返回统一排序结果。

共性结论：

1. **不要只靠 dense vector**，否则精确词和编号召回弱。
2. **不要只靠 BM25**，否则语义改写、同义表达召回弱。
3. **分数不要简单相加**，dense 和 sparse 分数尺度不同，优先用 RRF 或 ranker。
4. **权限过滤仍要做最终 DB 终检**，避免检索引擎 metadata 不一致导致泄露。

## 3. 推荐路线

推荐将方案调整为：

```text
优先方案：Milvus 原生 Hybrid Search
备选方案：MySQL LIKE / FULLTEXT 作为短期兜底或无法升级 Milvus 时的退路
后续方案：OpenSearch / Elasticsearch 作为大规模全文检索增强
```

也就是说，不优先做 MySQL LIKE 版 hybrid。当前项目已经把 chunk 内容和 dense embedding 写入 Milvus，继续在 Milvus 内扩展 sparse/BM25 字段，架构更集中。

## 4. 当前项目现状

当前 Milvus collection schema 由 `MilvusCollectionHelper` 创建：

```text
id        VarChar PK
content   VarChar
metadata  JSON
embedding FloatVector
```

当前只支持一个 dense 向量字段 `embedding`，检索入口是：

```java
vectorSyncService.searchSimilar(question, topK, contextDoc)
```

底层调用：

```java
milvusClient.search(...)
```

要使用 Milvus 原生 hybrid search，需要将 collection schema 扩展为 dense + sparse 双向量结构。

## 5. Milvus 原生 Hybrid Search 目标架构

目标链路：

```text
文档上传
  -> Tika 解析
  -> 文档预处理
  -> chunk
  -> dense embedding
  -> 写入 Milvus: content + metadata + dense_vector + sparse_vector

用户问题
  -> query dense embedding
  -> query sparse/BM25 representation
  -> Milvus hybridSearch
       -> dense ANN
       -> sparse ANN / BM25
       -> ranker fusion
  -> DB 查询文档和 chunk
  -> 权限终检
  -> 返回 matchedChunks
```

建议新增抽象：

```text
RagQaTool
  -> RagRetrievalService
       -> MilvusHybridRetrievalProvider
       -> RetrievalPermissionFilter
       -> RetrievalResultAssembler
```

`RagQaTool` 不再直接编排向量召回、文档查询、权限过滤和结果组装。

## 6. Milvus Collection Schema 设计

建议新 collection schema：

```text
id             VarChar PK
content        VarChar
metadata       JSON
dense_vector   FloatVector
sparse_vector  SparseFloatVector
```

字段说明：

1. `content`：chunk 文本，供 sparse/BM25 和返回内容使用。
2. `metadata`：保留 `doc_id`、`chunk_index`、`document_status`、权限粗过滤字段、预处理 metadata。
3. `dense_vector`：当前 embedding 模型生成的语义向量。
4. `sparse_vector`：BM25 或 sparse encoder 生成的稀疏向量。

如果使用 Milvus BM25 function，可由 `content` 生成 sparse 表示；如果当前部署版本或 Java SDK 不满足 function 接入条件，则由服务端生成 sparse vector 后写入。

## 7. 索引设计

dense vector：

```text
field: dense_vector
metric: COSINE
index: AUTOINDEX 或 HNSW
```

sparse vector：

```text
field: sparse_vector
metric: IP
index: SPARSE_INVERTED_INDEX
```

注意：

1. dense metric 继续沿用当前 COSINE。
2. sparse 检索一般使用 IP。
3. 现有 collection 只有 `embedding`，不能无损直接变成双向量 schema；建议新建 collection 并重建索引。

## 8. 查询融合策略

优先使用 Milvus ranker / RRF。

推荐第一版：

```text
dense topN = topK * 5
sparse topN = topK * 5
fusion = RRF
rrfK = 60
```

RRF 适合作为第一版，因为 dense 分数和 sparse/BM25 分数尺度不同，不需要手动归一化。

示例：

```text
chunk A: dense rank=1, sparse 未命中
chunk B: dense rank=8, sparse rank=1
chunk C: dense rank=3, sparse rank=4
```

融合后，C 可能更靠前，因为它在两路召回中都表现稳定。

## 9. 权限过滤策略

权限必须分两层：

### 9.1 Milvus 粗过滤

Milvus metadata 中可带：

```json
{
  "doc_id": "1001",
  "document_status": "SUCCESS",
  "document_enabled": true,
  "chunk_enabled": true,
  "permission_type": "DEPARTMENT",
  "department_id": "10",
  "project_ids": ["20"],
  "user_ids": ["1001"]
}
```

查询时尽量用 filter 减少无效召回：

```text
document_status == SUCCESS
document_enabled == true
chunk_enabled == true
权限粗过滤条件
```

### 9.2 DB 终检

无论 Milvus filter 做得多完整，最终仍必须查 DB：

1. `kb_document.deleted = 0`
2. `kb_document.status = SUCCESS`
3. `kb_document.enabled = 1`
4. `kb_document_chunk.enabled = 1`
5. `DocumentVisibilityService.canView(...)`

原因：权限变更和 Milvus metadata 同步可能存在延迟，DB 才是最终可信源。

## 10. 数据迁移策略

由于现有 collection schema 不包含 sparse vector，建议采用新 collection 迁移：

```text
旧 collection: kb_chunk_embedding
新 collection: kb_chunk_hybrid_v1
```

迁移步骤：

1. 新增 hybrid collection 创建逻辑。
2. 增加配置开关：

```yaml
app:
  rag:
    retrieval:
      mode: VECTOR_ONLY
  milvus:
    hybrid-collection: kb_chunk_hybrid_v1
```

3. 对历史 `SUCCESS` 文档执行重建索引。
4. 写入新文档时同时写入 hybrid collection。
5. 灰度切换 `retrieval.mode=HYBRID_MILVUS`。
6. 验证效果后停止旧 collection 写入。

回滚方式：

```text
retrieval.mode=VECTOR_ONLY
```

继续使用旧 dense vector 检索。

## 11. 代码改造点

### 11.1 MilvusCollectionHelper

新增 hybrid collection schema 创建：

```text
id
content
metadata
dense_vector
sparse_vector
```

保留旧 collection 创建逻辑，避免破坏现有环境。

### 11.2 MilvusVectorWriter

新增写入 hybrid row：

```text
id
content
metadata
dense_vector
sparse_vector
```

新增 hybrid search 方法：

```java
List<SearchResult> hybridSearch(
        String collectionName,
        float[] denseVector,
        SparseVector sparseVector,
        int topK,
        String filter
);
```

### 11.3 VectorSyncService

新增接口：

```java
List<SearchResult> hybridSearchSimilar(String query, int topK, KbDocument context);
```

内部负责：

1. 生成 dense query vector。
2. 生成 sparse query vector 或 BM25 query。
3. 调 Milvus hybrid search。

### 11.4 RagRetrievalService

新增统一 RAG 检索服务，职责：

1. 读取检索模式。
2. 执行 `VECTOR_ONLY` / `HYBRID_MILVUS`。
3. 查询 DB chunk 和 document。
4. 做权限终检。
5. 按文档聚合 matchedChunks。

### 11.5 RagQaTool

从直接调用 `vectorSyncService.searchSimilar(...)` 改成：

```java
ragRetrievalService.retrieve(question, topK, user);
```

## 12. 配置建议

```yaml
app:
  rag:
    retrieval:
      mode: HYBRID_MILVUS
      top-n-multiplier: 5
      ranker:
        type: RRF
        rrf-k: 60
      min-score:
        enabled: false
  milvus:
    collection: kb_chunk_embedding
    hybrid-collection: kb_chunk_hybrid_v1
```

`mode` 可选：

1. `VECTOR_ONLY`：沿用当前单 dense vector 检索。
2. `HYBRID_MILVUS`：使用 Milvus 原生 hybrid search。
3. `KEYWORD_DB`：仅作为 Milvus hybrid 不可用时的临时 fallback。

## 13. 与 MySQL / OpenSearch 方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|---|---|---|---|
| Milvus 原生 hybrid | 架构集中；dense+sparse 一体；适合当前 Milvus 技术栈 | 需要新 schema、重建索引、确认 SDK/服务端版本 | 推荐 |
| MySQL LIKE | 改动小；无新依赖 | 慢；中文检索差；数据量大后不可控 | 只适合临时兜底 |
| MySQL FULLTEXT | 比 LIKE 好；无新中间件 | 中文分词和排序能力有限 | 中期过渡 |
| OpenSearch/Elasticsearch | BM25、中文分词、同义词、过滤强 | 多一套基础设施和同步链路 | 大规模阶段再考虑 |

## 14. 测试计划

至少覆盖：

1. `VECTOR_ONLY` 能保持旧行为。
2. `HYBRID_MILVUS` 同时使用 dense 和 sparse 请求。
3. dense 命中、sparse 未命中时仍返回结果。
4. sparse 命中、dense 排名较低时能被提升。
5. 同一 chunk 被两路命中时只返回一次。
6. RRF/ranker 排序符合预期。
7. 无权限文档不返回。
8. disabled chunk 不返回。
9. `FAILED` 文档不返回。
10. Milvus hybrid 调用失败时可按配置回退到 `VECTOR_ONLY` 或返回明确错误。

## 15. 风险

1. **Milvus / Java SDK 版本风险**：当前项目使用 `milvus-sdk-java 2.4.4`，需要验证 hybrid search、sparse vector、BM25 function 的 Java API 是否满足需求；必要时升级到 2.5+。
2. **collection 迁移风险**：现有 schema 不包含 sparse vector，不能简单原地改造，建议新建 collection。
3. **权限 metadata 一致性风险**：Milvus metadata 可能滞后，必须保留 DB 终检。
4. **中文分词风险**：如果使用 Milvus BM25 function，需要验证中文 tokenizer/analyzer 效果；不满足时要考虑外部 sparse encoder 或 OpenSearch。
5. **写入链路复杂度上升**：chunk 写入时要同时处理 dense 和 sparse，失败补偿要补齐。

## 16. 推荐落地顺序

### 阶段一：技术验证

1. 用测试 collection 验证 Milvus hybrid schema。
2. 验证 Java SDK 是否支持目标 API。
3. 写少量 chunk 数据，验证 dense+sparse hybrid search。
4. 对比纯 vector 和 hybrid 的召回结果。

### 阶段二：服务内接入

1. 增加 hybrid collection 创建逻辑。
2. 增加 hybrid 写入逻辑。
3. 增加 `RagRetrievalService`。
4. `RagQaTool` 改为使用检索服务。
5. 保留 `VECTOR_ONLY` 回滚模式。

### 阶段三：重建历史索引

1. 对历史 `SUCCESS` 文档重新构建 hybrid 向量。
2. 记录重建进度和失败日志。
3. 灰度切换默认检索模式为 `HYBRID_MILVUS`。

### 阶段四：效果评测

1. 建立制度编号、专有名词、人名、项目名、表格字段问题集。
2. 对比 vector only 和 hybrid 的 recall@k、answer hit rate。
3. 根据评测结果调整 ranker 和 topN。

## 17. 结论

当前项目优先推荐 Milvus 原生 hybrid search，而不是 MySQL LIKE 版 hybrid。

原因：

1. 项目已经使用 Milvus 存 chunk 和 dense vector。
2. 原生 hybrid 能把 dense 和 sparse 召回集中在同一检索引擎里。
3. RRF/ranker 融合比手写分数归一化更稳。
4. DB 权限终检可以继续保留，安全边界不变。
5. MySQL LIKE 只适合作为版本不满足或短期验证时的兜底方案。
