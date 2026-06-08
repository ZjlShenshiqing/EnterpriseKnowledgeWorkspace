# RAG Hybrid Search 实现总结

## 1. 改动目标

本次 `milvus-hybrid-search` 变更的目标是补强知识库 RAG 检索能力，在保留现有 dense vector 检索链路的基础上，引入 Milvus hybrid search：

```text
dense vector 负责语义相似召回
sparse vector 负责关键词、编号、缩写、专有名词召回
RRF 负责融合两路结果
DB 权限终检负责兜底安全
```

默认配置仍为 `VECTOR_ONLY`，因此旧链路可继续运行；切换到 `HYBRID_MILVUS` 后，系统使用 hybrid collection 做双路召回，并在异常时回退到 dense vector 检索。

## 2. 总体链路

### 2.1 写入链路

```text
文档解析和分块
  -> 生成 chunk
  -> dense embedding
  -> 写入旧 dense collection: kb_chunk_embedding
  -> HYBRID_MILVUS 模式下额外生成 sparse vector
  -> 写入 hybrid collection: kb_chunk_hybrid_v1
```

`HYBRID_MILVUS` 模式下，写入是双写：

1. 继续写旧 dense collection，保证 fallback 可用。
2. 同时写 hybrid collection，保证 hybrid search 有数据可查。

涉及入口：

1. `syncChunk`
2. `syncChunks`
3. `indexDocumentChunks`
4. `updateChunk`
5. `deleteDocumentVectors`
6. `deleteChunkVector`
7. `deleteChunkVectors`

### 2.2 查询链路

```text
RagQaTool
  -> RagRetrievalService.retrieve(...)
  -> VectorSyncService.searchSimilar(...)
       -> VECTOR_ONLY: dense search
       -> HYBRID_MILVUS: dense query + sparse query + RRF
          -> hybrid 失败时 fallback 到 dense search
  -> 查询 DB 文档和 chunk
  -> DocumentVisibilityService 权限终检
  -> 返回 ToolResult
```

## 3. 配置项

配置位于 `enterprise-knowledge-ai-service/src/main/resources/application.yml`：

```yaml
app:
  rag:
    retrieval:
      mode: VECTOR_ONLY
      top-n-multiplier: 5
      ranker:
        type: RRF
        rrf-k: 60
      min-score:
        enabled: false
        value: 0.3
  milvus:
    collection: kb_chunk_embedding
    hybrid-collection: kb_chunk_hybrid_v1
    vector-dimension: 1024
    sparse-dimension: 65535
```

关键说明：

1. `app.rag.retrieval.mode=VECTOR_ONLY`：默认旧链路，只走 dense vector。
2. `app.rag.retrieval.mode=HYBRID_MILVUS`：启用 hybrid search。
3. `top-n-multiplier`：dense 和 sparse 每路召回候选数为 `topK * multiplier`。
4. `rrf-k`：RRF 融合参数，默认 60。
5. `hybrid-collection`：新双向量 collection 名，默认 `kb_chunk_hybrid_v1`。
6. `sparse-dimension`：本地 sparse term 哈希空间大小。

## 4. Milvus Collection

### 4.1 旧 dense collection

```text
id        VarChar PK
content   VarChar
metadata  JSON
embedding FloatVector + COSINE
```

旧 collection 保持不变，用于默认检索和 hybrid fallback。

### 4.2 新 hybrid collection

```text
id             VarChar PK
content        VarChar
metadata       JSON
dense_vector   FloatVector + COSINE
sparse_vector  SparseFloatVector + IP
```

`MilvusCollectionBootstrap` 在启动时始终初始化旧 dense collection；当 `mode=HYBRID_MILVUS` 时，额外初始化 hybrid collection。

## 5. Sparse Vector 生成

新增 `SparseVectorGenerator`，不依赖外部分词库或远程模型。

当前策略：

1. 中文：字符 unigram + 相邻中文 bigram。
2. 英文和数字：按空白和标点拆分。
3. 权重：词频归一化。
4. term 位置：按 `hashCode` 映射到 `sparse-dimension` 空间。

空文本返回空 map，并记录 warning。hybrid 写入要求 sparse vector 非空；如果 chunk 内容为空，`MilvusVectorWriter` 会拒绝写入。

## 6. 检索融合

`MilvusVectorWriter.hybridSearch(...)` 使用 SDK 兼容的双 search + 手动 RRF：

```text
dense search:  topK * topNMultiplier
sparse search: topK * topNMultiplier
fusion:        RRF score = Σ 1 / (rrfK + rank)
return:        fused topK
```

这样避免直接混加 dense score 和 sparse score，因为两者分数尺度不同。

## 7. 权限与过滤

权限仍分两层处理。

### 7.1 Milvus 粗过滤

hybrid metadata 会补齐以下字段：

```json
{
  "document_status": "SUCCESS",
  "document_enabled": true,
  "chunk_enabled": true
}
```

hybrid 查询时使用粗过滤：

```text
metadata["document_status"] == "SUCCESS"
&& metadata["document_enabled"] == true
&& metadata["chunk_enabled"] == true
```

### 7.2 DB 终检

`RagRetrievalServiceImpl` 会查 DB 做最终判断：

1. 文档未逻辑删除。
2. 文档状态为 `SUCCESS`。
3. 文档启用。
4. chunk 启用。
5. `DocumentVisibilityService.canView(...)` 权限通过。

DB 仍是最终可信源，Milvus metadata 只负责减少无效候选。

## 8. 关键文件

### 配置与属性

1. `RagRetrievalProperties.java`：RAG 检索模式、RRF、min-score 配置。
2. `MilvusProperties.java`：新增 `hybridCollection`、`sparseDimension`。
3. `application.yml`：新增 `app.rag.retrieval.*` 和 hybrid collection 配置。

### Milvus 层

1. `MilvusCollectionHelper.java`：新增 hybrid collection schema 创建与 load。
2. `MilvusCollectionBootstrap.java`：启动时按模式初始化 dense / hybrid collection。
3. `MilvusVectorWriter.java`：新增 hybrid 写入、upsert、sparse search、RRF fusion。
4. `VectorDocChunk.java`：新增 `sparseVector` 字段。
5. `SparseVectorGenerator.java`：本地 sparse vector 生成。

### 服务层

1. `VectorSyncService.java`：新增 `hybridSearchSimilar(...)`。
2. `VectorSyncServiceImpl.java`：模式分发、fallback、hybrid 双写、hybrid 删除同步。
3. `RagRetrievalService.java`：统一 RAG 检索入口。
4. `RagRetrievalServiceImpl.java`：检索、DB 查询、权限终检、结果组装。
5. `RetrievalResult.java`：文档和 chunk 检索结果 DTO。

### Tool 层

1. `RagQaTool.java`：不再直接做向量检索、DB 查询和权限过滤，改为委托 `RagRetrievalService`。

## 9. 兼容性与回滚

### 9.1 默认兼容

默认配置：

```yaml
app:
  rag:
    retrieval:
      mode: VECTOR_ONLY
```

默认模式下：

1. 不要求 hybrid collection 必须存在。
2. 检索仍走旧 dense collection。
3. 写入仍可保持旧 dense collection。
4. `RagQaTool` 输出结构不变。

### 9.2 启用 hybrid

启用方式：

```yaml
app:
  rag:
    retrieval:
      mode: HYBRID_MILVUS
```

启用后：

1. 启动时初始化 `kb_chunk_hybrid_v1`。
2. 新增或同步 chunk 时写入 hybrid collection。
3. 检索时走 dense + sparse + RRF。
4. hybrid search 失败时自动 fallback 到 dense search。

### 9.3 回滚

回滚只需要切回：

```yaml
app:
  rag:
    retrieval:
      mode: VECTOR_ONLY
```

旧 collection 和旧 dense 检索链路仍保留。

## 10. 历史数据处理

新 hybrid collection 不会自动拥有历史数据。历史 `SUCCESS` 文档需要执行一次重建索引或重新同步 chunk，才能进入 `kb_chunk_hybrid_v1`。

引入 IK Analyzer 后，即使 hybrid collection 已经包含历史数据，也必须重新构建全部 hybrid 索引。原因是文档 sparse vector 改为 IK 最大化分词，而查询 sparse vector 改为 IK 智能分词；旧 sparse vector 与新查询分词不一致会直接影响关键词召回。dense collection、数据库表和 Milvus schema 不需要迁移。

当前已提供后台重建接口：

```http
POST /api/kb/admin/hybrid-index/rebuild?limit=100
POST /api/kb/admin/hybrid-index/documents/{documentId}/rebuild
```

说明：

1. 接口只允许管理员调用。
2. 批量接口只扫描 `SUCCESS` 文档。
3. 重建时即使当前仍是 `VECTOR_ONLY`，也会强制写入 hybrid collection，便于灰度前预热。
4. 单个文档重建会先删除该文档在 hybrid collection 中的旧向量，再重新写入 dense + sparse 双向量。
5. 返回值包含扫描数、成功数、跳过数、失败数、成功写入切片数、失败文档 ID 和跳过文档 ID。

建议流程：

1. 部署代码，保持 `VECTOR_ONLY`。
2. 创建并验证 hybrid collection。
3. 使用管理员身份调用批量重建接口，设置足以覆盖当前成功文档数的 `limit`；接口当前最多接受 1000，且没有分页游标。
4. 对失败文档使用单文档接口重试，并确认失败文档 ID 为空；如果成功文档超过 1000，需使用单文档接口补齐或先为批量接口增加分页能力，不能依赖重复调用批量接口。
5. 抽样验证中文词语和 `OA-2025-001` 这类完整编号能够通过 sparse 检索命中。
6. 切换 `HYBRID_MILVUS` 灰度。
7. 观察召回效果、hybrid fallback 日志和 IK fallback 警告。

## 11. 测试覆盖

新增或更新测试覆盖：

1. `SparseVectorGeneratorTest`
   - 空文本返回空 sparse vector。
   - 中文文本可生成 sparse vector。
   - 短 query 可生成 sparse vector。
   - 中英文混合和编号类文本可处理。
   - 权重归一化。

2. `VectorSyncServiceImplHybridTest`
   - `VECTOR_ONLY` 模式不写 hybrid collection。
   - `HYBRID_MILVUS` 模式写 hybrid collection。
   - hybrid 写入时生成 sparse vector。
   - batch sync 时补齐过滤 metadata。
   - update chunk 时 upsert hybrid collection。
   - 历史重建即使在 `VECTOR_ONLY` 模式下也能强制写 hybrid collection。

3. `HybridIndexRebuildServiceImplTest`
   - 文档不存在时返回业务错误。
   - 非 `SUCCESS` 文档会跳过，不写 Milvus。
   - 批量重建时单文档失败不影响后续文档。

4. `MilvusCollectionBootstrapTest`
   - `VECTOR_ONLY` 只初始化 dense collection。
   - `HYBRID_MILVUS` 同时初始化 dense 和 hybrid collection。

5. `RagQaToolTest`
   - `RagQaTool` 委托 `RagRetrievalService` 后输出结构保持兼容。

验证命令：

```bash
mvn test -pl enterprise-knowledge-ai-service
```

最近一次验证结果：

```text
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 12. 当前边界

本次实现完成了 hybrid search 主链路，但仍有一些明确边界：

1. sparse vector 使用本地轻量 tokenizer + term frequency，不是完整 BM25 语料级 IDF。
2. 没有引入 jieba、OpenSearch、Elasticsearch 或外部 sparse encoder。
3. 历史索引重建是后台同步接口，不是带进度持久化的异步任务。
4. hybrid collection schema 变更仍建议通过新 collection 迁移，不建议原地修改旧 collection。
5. 还没有真实 Milvus 服务端集成测试；当前测试主要覆盖服务编排、配置分支和本地 sparse 生成。

## 13. 后续建议

1. 将历史文档 hybrid 索引重建升级为异步任务，记录批次进度和失败明细。
2. 增加真实 Milvus 集成测试，验证 `SparseFloatVector` 写入和检索兼容当前服务端版本。
3. 按真实问答日志调优 `top-n-multiplier`、`rrf-k`、`min-score`。
4. 若编号、制度条款和表格字段仍不稳定，可替换 `SparseVectorGenerator` 为更强分词或 sparse encoder。
5. 后续接入 reranking 时，建议在 RRF 后、DB 终检后对候选 chunk rerank。
