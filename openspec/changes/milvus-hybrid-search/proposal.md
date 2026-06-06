## Why

当前 RAG 检索仅依赖 Milvus 单路 dense vector 召回，对精确词、制度编号、项目名、人名、表格字段、缩写、编码类问题不稳定。需要引入 hybrid search（dense + sparse/BM25）提升关键词召回能力，同时保持架构集中在已使用的 Milvus 上，避免引入 MySQL LIKE 或新的中间件。

## What Changes

- 新增 Milvus hybrid collection schema，支持 `dense_vector` + `sparse_vector` 双向量字段
- 新增 `RagRetrievalService` 统一检索入口，替代 `RagQaTool` 直接调用 `VectorSyncService.searchSimilar()` 的模式
- `MilvusVectorWriter` 扩展 hybrid 写入和 hybrid search 方法
- `VectorSyncService` 新增 `hybridSearchSimilar()` 接口
- `EmbeddingService` 新增 sparse embedding 生成能力
- 新增检索模式配置 `app.rag.retrieval.mode`（VECTOR_ONLY / HYBRID_MILVUS）
- 权限过滤保留两层：Milvus metadata 粗过滤 + DB 终检
- **BREAKING**: 无。`VECTOR_ONLY` 模式保持旧行为不变

## Capabilities

### New Capabilities

- `rag-retrieval`: 统一 RAG 检索服务，支持多检索模式切换、权限终检、结果组装
- `milvus-hybrid-collection`: 新 collection schema 创建与管理，支持 dense + sparse 双向量
- `sparse-embedding`: 稀疏向量生成能力，支持 BM25 或 sparse encoder
- `hybrid-search-config`: 检索模式配置与回滚能力

### Modified Capabilities

<!-- 现有 spec 无，不修改已有能力的行为契约 -->

## Impact

- **MilvusCollectionHelper**: 新增 hybrid collection 创建方法
- **MilvusVectorWriter**: 新增 hybrid 写入和 hybrid search 方法
- **VectorSyncService / VectorSyncServiceImpl**: 新增 hybridSearchSimilar 接口
- **ChunkVectorStore / MilvusChunkVectorStore**: 新增 hybrid search 抽象
- **EmbeddingService / BailianEmbeddingService**: 新增 sparse embedding
- **RagQaTool**: 改为注入 RagRetrievalService
- **VectorDocChunk**: 新增 sparseVector 字段
- **MilvusProperties / KnowledgeAiProperties**: 新增 hybrid 配置项
- **application.yml**: 新增 `app.rag.retrieval.*` 配置块
- **KbKnowledgeBase**: 可选新增 hybrid 相关字段
