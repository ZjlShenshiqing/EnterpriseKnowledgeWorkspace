## Context

当前 RAG 检索链路为：`RagQaTool → VectorSyncService.searchSimilar() → EmbeddingService.embed() → MilvusVectorWriter.search()`，仅使用 Milvus 单路 dense vector（FloatVector + COSINE）召回。对精确词、制度编号、项目名、人名、缩写等关键词类问题不稳定。

现有 Milvus collection schema（`kb_chunk_embedding`）只有 `id / content / metadata / embedding` 四个字段，无 sparse vector 字段。项目使用 `milvus-sdk-java 2.4.4`，该版本已支持 `SparseFloatVec` 和 hybrid search API。

## Goals / Non-Goals

**Goals:**
- 在 Milvus 内扩展 dense + sparse 双向量 hybrid search，不引入新中间件
- 新增 `RagRetrievalService` 统一检索入口，分离检索编排与 Tool 逻辑
- 权限过滤保留两层（Milvus metadata 粗过滤 + DB 终检）
- 通过配置开关支持 `VECTOR_ONLY` / `HYBRID_MILVUS` 双模式，保证可回滚

**Non-Goals:**
- 不引入 MySQL LIKE / FULLTEXT 作为 hybrid 主方案（仅保留为极端兜底）
- 不引入 OpenSearch / Elasticsearch 作为检索引擎
- 不修改现有 `embedding` 字段和旧 collection 的行为
- 不改变 MCP Tool 协议（`rag_qa` 的输入输出不变）

## Decisions

### 1. 新建 collection 而非原地修改现有 collection

**选择**: 新建 `kb_chunk_hybrid_v1` collection，旧 `kb_chunk_embedding` 保持不变。

**原因**: 现有 collection 的 schema 不支持直接添加 vector 字段（Milvus 不允许对已有 collection 添加向量字段）。新建 collection 可以：
- 保留旧 collection 作回滚（切换配置即可回到 VECTOR_ONLY 模式）
- 避免数据迁移期间的中断

**替代方案**: 原地 drop 后重建 — 不可接受，会丢失所有历史向量数据且无法回滚。

### 2. sparse vector 由服务端生成后写入，优先使用 BM25

**选择**: 先由服务端对 chunk content 生成 sparse vector（BM25 或本地 sparse encoder），写入 Milvus `sparse_vector` 字段。

**原因**:
- `milvus-sdk-java 2.4.4` 的 BM25 function 支持取决于服务端版本（Milvus 2.4+），需要验证
- 如果 BM25 function 不可用，服务端生成 sparse vector 的兼容性更好
- 后续可切换到 Milvus native BM25 function

**替代方案**: 依赖外部 sparse encoder（BGE-M3、Splade 等）调用远程 API — 引入额外延迟和成本，先不做。

### 3. 融合策略用 RRF，不用分数归一化

**选择**: 第一版使用 RRF (Reciprocal Rank Fusion) 融合 dense 和 sparse 结果。

**原因**:
- dense 相似度分数和 sparse/BM25 分数的尺度不同，直接相加会偏重一方
- RRF 仅依赖排名而非绝对分数，不需要手动归一化
- Milvus SDK 2.4.4 已支持 `AnnSearchReq` + `RerankReq` 组合

**参数**: dense topN = topK × 5, sparse topN = topK × 5, RRF k = 60

**替代方案**: 加权分数融合 — 需要额外调参和归一化逻辑，不选。

### 4. RagRetrievalService 作为统一检索入口

**选择**: 新增 `RagRetrievalService` 接口及其实现，承担：检索模式读取 → 执行检索 → 查 DB → 权限终检 → 结果组装。

**原因**: `RagQaTool` 当前同时包含检索编排、DB 查询、权限过滤和结果组装，超过 200 行。抽取检索服务后：
- `RagQaTool` 只负责 Tool 协议适配（参数解析、结果格式化）
- 检索逻辑可复用（其他 Tool 或 API 可直接注入）
- 便于测试（可 mock 检索服务）

### 5. 稀疏 embedding 视为内部实现，不暴露到 EmbeddingService 接口体系

**选择**: sparse 表示生成逻辑封装在 `VectorSyncService` 内部或新增的 `SparseVectorGenerator` 中，不扩展 `EmbeddingService` 接口。

**原因**: sparse 生成（BM25 分词 + 词频统计）与 semantic embedding（调用远程模型 API）是两类能力。混在同一个接口会增加概念混淆。后续如果需要远程 sparse encoder，再抽象接口不迟。

## Risks / Trade-offs

- **Milvus SDK 版本风险**: `milvus-sdk-java 2.4.4` 的 hybrid search API 可能不完全满足需求（如 `hybridSearch()` 方法签名可能不同）。→ 缓解：阶段一先用测试 collection 验证 API 可用性，不满足则升级 SDK 到 2.5+。
- **Milvus 服务端版本风险**: BM25 function 需要 Milvus 2.4+ 服务端。→ 缓解：优先走服务端生成 sparse vector 的路径，不依赖 BM25 function。
- **中文分词效果**: 本地 BM25 的中文分词（jieba 等）可能不如专用搜索引擎。→ 缓解：第一版用简单的字符级 n-gram 或 jieba 分词，后续可替换为更好的分词方案。
- **collection 迁移期间的写入放大**: 过渡期需同时写旧 collection 和新 hybrid collection。→ 缓解：通过配置开关控制是否写 hybrid collection，文档写入链路增加有限。

## Migration Plan

1. 阶段一（技术验证）: 用测试 collection 验证 SDK API、schema 创建、hybrid search 可用性
2. 阶段二（服务接入）: 增加 hybrid collection 创建逻辑、hybrid 写入逻辑、RagRetrievalService
3. 阶段三（历史索引）: 对历史 SUCCESS 文档重建 hybrid 索引
4. 阶段四（灰度切换）: 配置切到 `HYBRID_MILVUS`，监控效果

**回滚**: 配置 `app.rag.retrieval.mode=VECTOR_ONLY` 即可回到纯 dense vector 检索。
