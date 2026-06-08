# RAG Hybrid Search 改造现状总结

## 1. 一句话结论

当前 RAG 已经从“只靠语义找内容”的纯 dense vector 检索，改造成了可灰度的 hybrid search 主链路：

```text
文档写入/重建 -> dense + sparse 双向量
查询 -> dense + sparse 双路召回
排序 -> RRF 融合
终检 -> DB 文档/Chunk/权限过滤
异常 -> fallback 到 VECTOR_ONLY
灰度/回滚 -> 配置切换
```

但它还不是最终生产完整态。当前缺口主要是：rerank 还未实现、sparse 仍是轻量 hash 词频方案、历史重建是同步后台接口而不是带进度持久化的异步任务。

## 2. 当前已经实现的能力

### 2.1 写入侧：dense + sparse 双向量

在 `HYBRID_MILVUS` 模式下，新写入、批量写入、Chunk 更新会同时写：

1. 原 dense collection。
2. 新 hybrid collection：`dense_vector` + `sparse_vector`。

相关文件：

1. `VectorSyncServiceImpl`
2. `MilvusVectorWriter`
3. `SparseVectorGenerator`
4. `MilvusCollectionHelper`

### 2.2 查询侧：dense + sparse 双路召回

查询时会生成：

1. dense query embedding。
2. sparse query vector。

然后分别召回 dense 结果和 sparse 结果，再做融合。

当前实现不是直接依赖 Milvus SDK 的内置 hybrid ranker，而是 Java 侧分别 search 后手动做 RRF。这样对 SDK 版本更稳，但后续如果 Milvus 版本确认支持，也可以切成原生 `hybridSearch` + ranker。

### 2.3 RRF 融合排序

当前融合逻辑：

```text
dense topK * multiplier
sparse topK * multiplier
按 chunkId + docId 去重
按 RRF 分数合并
返回最终 topK
```

配置项：

```yaml
app:
  rag:
    retrieval:
      top-n-multiplier: 5
      ranker:
        type: RRF
        rrf-k: 60
```

### 2.4 DB 权限终检

Milvus 侧只做粗过滤：

```text
document_status == SUCCESS
document_enabled == true
chunk_enabled == true
```

真正的权限终检仍在 DB 侧做：

1. 查 `kb_document`。
2. 查 `kb_document_chunk`。
3. 查 `kb_document_permission`。
4. 调用 `DocumentVisibilityService.canView()`。

这样可以避免无权限文档标题、摘要、片段、来源泄露。

### 2.5 fallback 和灰度

当前默认配置仍是：

```yaml
app:
  rag:
    retrieval:
      mode: VECTOR_ONLY
```

切到 hybrid：

```yaml
app:
  rag:
    retrieval:
      mode: HYBRID_MILVUS
```

如果 hybrid 检索失败，代码会自动 fallback 到 dense-only 检索。

回滚也简单：把 `mode` 切回 `VECTOR_ONLY`。

## 3. 本次新增的历史数据重建能力

因为旧 collection 只有 dense vector，没有 sparse vector，所以历史数据不能直接原地升级，必须重建 hybrid collection。

本次新增了后台接口：

```http
POST /api/kb/admin/hybrid-index/rebuild?limit=100
POST /api/kb/admin/hybrid-index/documents/{documentId}/rebuild
```

设计点：

1. 只允许管理员调用。
2. 批量接口只处理 `SUCCESS` 文档。
3. 即使当前检索模式仍是 `VECTOR_ONLY`，也会强制写入 hybrid collection。
4. 单文档重建会先删除该文档在 hybrid collection 中的旧向量，再重新写入。
5. 某个文档失败不会中断后续批量处理。
6. 返回扫描数、成功数、跳过数、失败数、写入 Chunk 数、失败文档 ID、跳过文档 ID。

示例返回：

```json
{
  "total": 100,
  "success": 96,
  "skipped": 2,
  "failed": 2,
  "chunkCount": 1830,
  "failedDocumentIds": [10001, 10002],
  "skippedDocumentIds": [10003, 10004]
}
```

## 4. 推荐上线步骤

### 阶段一：保持 VECTOR_ONLY 部署

先部署代码，但保持默认：

```yaml
app.rag.retrieval.mode: VECTOR_ONLY
```

此时线上问答仍走原 dense 检索，不改变用户体验。

### 阶段二：初始化 hybrid collection

确认 Milvus 中存在 hybrid collection：

```yaml
app:
  milvus:
    hybrid-collection: kb_chunk_hybrid_v1
    sparse-dimension: 65535
```

### 阶段三：重建历史索引

分批调用：

```http
POST /api/kb/admin/hybrid-index/rebuild?limit=100
```

直到历史 `SUCCESS` 文档基本写入 hybrid collection。

对失败文档可以单独补偿：

```http
POST /api/kb/admin/hybrid-index/documents/{documentId}/rebuild
```

### 阶段四：灰度切换 HYBRID_MILVUS

小范围切换：

```yaml
app.rag.retrieval.mode: HYBRID_MILVUS
```

观察：

1. hybrid 检索失败 fallback 日志。
2. 编号、专有名词、人名、项目名、表格字段命中率。
3. 问答上下文是否仍通过权限终检。
4. 延迟是否可接受。

### 阶段五：效果评测

建议准备一组固定问题集，对比：

1. vector only recall@k。
2. hybrid recall@k。
3. answer hit rate。
4. 无权限文档是否泄露。
5. 编号/条款/表格字段类问题是否提升。

## 5. 当前仍然存在的缺陷

### 5.1 没有 rerank

现在 RRF 后直接进入 DB 终检和上下文组装，没有 cross-encoder / LLM rerank。

影响：

1. 召回覆盖提升了，但最终排序还可能不够准。
2. “语义相近但不是答案”的 chunk 仍可能排在前面。

已补 OpenSpec：

```text
openspec/changes/add-rag-reranking
```

### 5.2 sparse 方案还比较轻量

当前 sparse 是本地 tokenizer + term frequency + hash 映射：

1. 中文按一元/二元字符处理。
2. 英文/数字按空白和标点切分。
3. 权重是词频归一化。
4. 没有语料级 IDF。
5. 不是 BM25、SPLADE 或学习型 sparse encoder。

短期优点是实现简单、无外部依赖、可快速提升编号/关键词命中。

长期缺点是排序能力有限，可能需要升级到：

1. BM25 sparse。
2. Milvus 官方 sparse embedding function。
3. SPLADE / bge-m3 sparse。
4. OpenSearch / Elasticsearch BM25 旁路召回。

### 5.3 历史重建还是同步接口

当前重建接口适合灰度前手动分批执行，但还不是完整生产任务系统。

后续建议增加：

1. 重建任务表。
2. 异步执行。
3. 进度查询。
4. 失败重试。
5. 按知识库、文档范围、时间范围重建。

### 5.4 还没有真实 Milvus 集成测试

目前测试主要覆盖：

1. 服务分支。
2. sparse 生成。
3. hybrid 写入编排。
4. fallback 编排。

还需要在真实 Milvus 环境验证：

1. `SparseFloatVector` schema 创建。
2. dense/sparse 双字段写入。
3. sparse search 结果。
4. metadata filter 表达式。
5. 大批量重建性能。

## 6. 当前代码改动范围

新增/修改的核心文件：

1. `HybridIndexRebuildService`
2. `HybridIndexRebuildServiceImpl`
3. `HybridIndexRebuildResult`
4. `VectorSyncService.rebuildHybridChunks(...)`
5. `VectorSyncServiceImpl.rebuildHybridChunks(...)`
6. `KbAdminController` hybrid index rebuild 接口
7. `HybridIndexRebuildServiceImplTest`
8. `VectorSyncServiceImplHybridTest`
9. `docs/rag-hybrid-search-implementation.md`
10. `docs/rag-hybrid-search-design.md`
11. `openspec/changes/milvus-hybrid-search/tasks.md`

## 7. 下一步建议

优先级建议：

1. 跑完整单测，确认本次重建接口编译和测试通过。
2. 在本地/测试 Milvus 环境执行一次 hybrid collection 初始化和单文档重建。
3. 准备固定问题集做 vector only vs hybrid 对比。
4. 灰度打开 `HYBRID_MILVUS`。
5. 实现 rerank OpenSpec。
6. 把 sparse 从轻量 hash 方案升级到 BM25 或学习型 sparse。
