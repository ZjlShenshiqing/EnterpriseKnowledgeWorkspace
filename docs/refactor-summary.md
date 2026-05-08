# 阶段 2A 架构重构 — 改动总结

## 时间

2026-05-07 ~ 2026-05-08

## 提交记录

| 提交 | 说明 |
|------|------|
| `refactor: 提取 VectorSyncService，合并重复的向量同步逻辑` | R1 |
| `refactor: 扁平化 Milvus 调用链，砍掉透传中间层` | R2 |
| `refactor: 拆分 KbDocumentServiceImpl 为独立职责服务` | R3 |
| `refactor: KbChunkServiceImpl 统一继承 ServiceImpl` | R4 |
| `refactor: chunk_count 改为查询计数，避免并发偏差` | R5 |
| `feat: 文件存储抽象层 + 文件下载接口` | R6 |

## R1 — 提取 VectorSyncService

**问题**：`KbDocumentServiceImpl` 和 `KbChunkServiceImpl` 各自实现了 `embedBatch()`、`embedContent()`、`toArray()`、向量同步逻辑，代码重复且不一致。

**改动**：
- 新建 `VectorSyncService`：统一管理嵌入生成与 Milvus 读写
  - `embed()` / `embedBatch()` — 统一嵌入调用，模型路由内置
  - `shouldEmbed()` — 全局开关 + 模型配置判断
  - `resolveCollection()` / `resolveCollectionOrDefault()` — 集合路由
  - `syncChunk()` / `syncChunks()` / `updateChunk()` — Chunk 向量同步
  - `deleteDocumentVectors()` / `deleteChunkVector()` / `deleteChunkVectors()` — 向量删除
  - `indexDocumentChunks()` — 预构建 VectorDocChunk 写入
  - `toArray()` — List<Float> → float[]
- `KbDocumentServiceImpl`：去掉 `EmbeddingService`、`ChunkVectorStore`、`KbMilvusRoutingService` 三个依赖，改为注入 `VectorSyncService`
- `KbChunkServiceImpl`：同样改为使用 `VectorSyncService`，删除重复的私有方法

## R2 — 扁平化 Milvus 调用链

**问题**：调用链 `ChunkVectorStore → MilvusChunkVectorStore → VectorStoreService → MilvusVectorStoreService → MilvusVectorWriter` 共 5 层，其中 `VectorStoreService`（接口）和 `MilvusVectorStoreService`（实现）是完全的透传层。

**改动**：
- 删除 `VectorStoreService.java`（接口）
- 删除 `MilvusVectorStoreService.java`（透传实现）
- 重写 `MilvusChunkVectorStore.java`：直接注入 `MilvusVectorWriter`，适配方法名差异（`upsertChunk`↔`updateChunk`、`deleteByDocumentId`↔`deleteDocumentVectors` 等）
- 调用链缩减为 3 层：`ChunkVectorStore → MilvusChunkVectorStore → MilvusVectorWriter`

## R3 — 拆分 KbDocumentServiceImpl

**问题**：`KbDocumentServiceImpl` 为 696 行，承担上传落盘、权限写入、Tika 解析、策略分块、向量化、Milvus 写入、启用/禁用、删除、搜索、chunk 日志查询等所有职责。

**改动**：
- 新建 `DocumentUploadService`（~170 行）：文件校验 → INSERT kb_document (PENDING) → 落盘 → MIME 探测 → 权限行
- 新建 `DocumentChunkingService`（~250 行）：startChunk (CAS 乐观锁) → executeChunk (Tika 解析 → 策略分块 → 向量化 → 持久化)
- 新建 `DocumentDeleteService`（~60 行）：权限校验 → 删 Milvus 向量 → 清 DB 关联
- `KbDocumentServiceImpl` 缩减为 ~280 行：保留 pageVisible / getVisible / updateDocument / enableDocument / pageChunkLogs / searchDocuments，委托 upload / startChunk / executeChunk / deleteVisible 给独立服务

## R4 — KbChunkServiceImpl 继承 ServiceImpl

**问题**：`KbChunkServiceImpl` 手动注入 `chunkMapper`，与 `KbDocumentServiceImpl` 继承 `ServiceImpl` 的风格不一致。

**改动**：
- `KbChunkServiceImpl` 改为 `extends ServiceImpl<KbDocumentChunkMapper, KbDocumentChunk>`
- 去掉手动注入的 `chunkMapper`，改用 `baseMapper`
- 18 处 `chunkMapper.xxx()` 替换为 `baseMapper.xxx()`

## R5 — chunk_count 改为查询计数

**问题**：`KbChunkServiceImpl` 使用 `setSql("chunk_count = chunk_count + 1")` 直接拼接 SQL，高并发下可能产生计数偏差。

**改动**：
- `KbDocumentService` 新增 `refreshChunkCount(docId)` 方法：`SELECT COUNT(1) FROM kb_document_chunk WHERE document_id=?`
- `KbChunkServiceImpl` 添加 `countChunksByDoc(docId)` 辅助方法
- 三处 `setSql` 替换为基于实际计数的更新

## R6 — 文件存储抽象

**问题**：文件直接写本地磁盘 `Paths.get(uploadDir)`，无抽象层，多实例无法共享，缺少下载接口。

**改动**：
- 新建 `FileStorageService` 接口：`store()` / `read()` / `delete()`
- 新建 `LocalFileStorageService`：本地磁盘实现
- `DocumentUploadService` 重构为使用 `FileStorageService`
- `KbDocumentController` 新增 `GET /api/kb/documents/{id}/download` 下载端点

## 文件变更统计

| 文件 | 状态 |
|------|------|
| `service/VectorSyncService.java` | 新增 |
| `service/FileStorageService.java` | 新增 |
| `service/impl/LocalFileStorageService.java` | 新增 |
| `service/impl/DocumentUploadService.java` | 新增 |
| `service/impl/DocumentChunkingService.java` | 新增 |
| `service/impl/DocumentDeleteService.java` | 新增 |
| `milvus/VectorStoreService.java` | 删除 |
| `milvus/MilvusVectorStoreService.java` | 删除 |
| `milvus/MilvusChunkVectorStore.java` | 重写 |
| `service/impl/KbDocumentServiceImpl.java` | 重写（696→280 行） |
| `service/impl/KbChunkServiceImpl.java` | 修改 |
| `service/KbDocumentService.java` | 修改（新增 refreshChunkCount） |
| `web/KbDocumentController.java` | 修改（新增 download） |
