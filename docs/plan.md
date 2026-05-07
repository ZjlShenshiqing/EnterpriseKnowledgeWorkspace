
# Plan 实施计划（完整修订版）

## 1. 当前进度总览

| 服务 | 状态 |
|------|------|
| enterprise-gateway-service | ✅ 完成（认证、JWT、RBAC、路由、限流、用户/部门/角色管理） |
| enterprise-knowledge-ai-service | ⚠️ Step3 完成，需重构 + Step4 待开发 |
| enterprise-collaboration-service | ⬜ 骨架（仅 Main.java） |
| enterprise-workbench-service | ⬜ 骨架（仅 Main.java） |

## 2. 知识库微服务现存问题（修复后再进入 Step4）

### 2.1 架构层面

1. **`KbDocumentServiceImpl` 过于臃肿（718 行）**：一个类承担上传落盘、权限行写入、Tika 解析、策略分块、向量化、Milvus 写入、启用/禁用、删除、搜索、chunk 日志查询，应拆分为独立职责的服务类。
2. **向量/嵌入逻辑在两处重复**：`KbDocumentServiceImpl` 和 `KbChunkServiceImpl` 各自实现了 `embedBatch()`、`embedContent()`、`toArray()`、向量同步逻辑，应提取到共享服务。
3. **Milvus 层过度抽象（5 层调用链）**：`ChunkVectorStore → MilvusChunkVectorStore → VectorStoreService → MilvusVectorStoreService → MilvusVectorWriter`，中间两层是纯透传，应扁平化。
4. **`KbChunkServiceImpl` 不遵循 MyBatis-Plus 规范**：手动注入 mapper 而非继承 `ServiceImpl`，与 `KbDocumentServiceImpl` 风格不一致。
5. **Chunk 服务越界直接操作 Document 表**：`KbChunkServiceImpl` 用 `setSql("chunk_count = chunk_count + 1")` 直接更新 `kb_document` 的 `chunk_count`。

### 2.2 数据一致性层面

6. **Chunk CRUD 的 DB 和 Milvus 不在同一事务**：`chunkMapper.insert()` 在 Spring 事务内，但 `chunkVectorStore.indexDocumentChunks()` 操作 Milvus gRPC 不在同一事务边界。
7. **手动 Chunk CRUD 不记录 chunk_log**：只有异步分块任务写 `kb_document_chunk_log`。
8. **异步分块失败后状态恢复不可靠**：`onChunkRequested` 的 catch 块只打日志不更新状态，依赖 `executeChunk` 内部 `markChunkFailed`，线程池拒绝等场景会卡在 RUNNING。
9. **`chunk_count` 增量更新不是原子操作**：`setSql("chunk_count = chunk_count + 1")` 高并发下可能计数偏差。

### 2.3 功能缺失层面

10. **无向量检索能力**：Milvus 只做写入，没有 Search 接口。
11. **智能问答模块完全未实现**：`/api/ai/qa/*` 无代码，`qa_conversation`/`qa_message`/`qa_feedback` 无 DDL。
12. **Embedding 是占位实现**：SHA-256 展开向量无语义意义。
13. **文件存储无抽象层**：直接写本地磁盘，无下载接口，多实例无法共享。

---

## 3. 阶段 2A：知识库架构重构

**目标**：解决代码膨胀、重复和调用链问题。

### R1 — 提取 VectorSyncService

将 `KbDocumentServiceImpl` 和 `KbChunkServiceImpl` 中重复的 embed/向量同步逻辑合并为一个服务。

```
git add -A && git commit -m "refactor: 提取 VectorSyncService，合并重复的向量同步逻辑"
```

### R2 — 扁平化 Milvus 调用链

砍掉 `MilvusChunkVectorStore` 和 `MilvusVectorStoreService` 两个透传层，`ChunkVectorStore` 直接委托 `MilvusVectorWriter`。

```
git add -A && git commit -m "refactor: 扁平化 Milvus 调用链，砍掉透传中间层"
```

### R3 — 拆分 KbDocumentServiceImpl

拆为 `DocumentUploadService`（上传+落盘+权限行）、`DocumentChunkingService`（异步分块+Tika+向量写入）、`DocumentDeleteService`（删除+向量清理）。原 `KbDocumentServiceImpl` 保留查询/启用等轻量方法。

```
git add -A && git commit -m "refactor: 拆分 KbDocumentServiceImpl 为独立职责服务"
```

### R4 — KbChunkServiceImpl 继承 ServiceImpl

去掉手动注入的 `chunkMapper`、`documentMapper`、`permissionMapper`，改为继承 `ServiceImpl<KbDocumentChunkMapper, KbDocumentChunk>`。

```
git add -A && git commit -m "refactor: KbChunkServiceImpl 统一继承 ServiceImpl"
```

### R5 — Chunk 操作通过 KbDocumentService 更新 chunk_count

去掉 `setSql("chunk_count = chunk_count + 1")`，改为调用 `KbDocumentService.refreshChunkCount(docId)`。

```
git add -A && git commit -m "refactor: Chunk 操作通过文档服务更新 chunk_count"
```

### R6 — 文件存储抽象

引入 `FileStorageService` 接口 + `LocalFileStorageService` 实现，为后续 MinIO/OSS 做准备。同时增加文件下载接口。

```
git add -A && git commit -m "feat: 文件存储抽象层 + 文件下载接口"
```

---

## 4. 阶段 2B：数据一致性与容错

### C1 — Chunk CRUD 事务边界修正

调整顺序：先写 Milvus（成功），再写 DB（事务内）。DB 失败时不回滚 Milvus（引入 outbox 补偿或接受最终一致性）。

```
git add -A && git commit -m "fix: 修正 Chunk CRUD 的 DB/Milvus 事务边界"
```

### C2 — 异步分块失败重试与状态恢复

`DocumentChunkEventListener` 加入 3 次指数退避重试，最终失败时确保落 `FAILED` + chunk_log。

```
git add -A && git commit -m "fix: 异步分块失败重试与状态恢复机制"
```

### C3 — 手动 Chunk 操作记录 chunk_log

`KbChunkServiceImpl` 的 create/update/delete/batchToggleEnabled 写入 `kb_document_chunk_log`。

```
git add -A && git commit -m "feat: 手动 Chunk 操作记录 chunk_log"
```

### C4 — chunk_count 改为查询计数

去掉增量更新，改为 `SELECT COUNT(1) FROM kb_document_chunk WHERE document_id=?`。

```
git add -A && git commit -m "fix: chunk_count 改为查询计数，避免并发偏差"
```

---

## 5. 阶段 2C：检索能力（Step4 前半）

### S1 — Milvus 向量检索

`VectorStoreService` 增加 `search()` 方法，接入 Milvus `SearchReq`，支持 top-K 相似度检索。

```
git add -A && git commit -m "feat: Milvus 向量相似度检索"
```

### S2 — 接入真实 Embedding 模型

替换 `PlaceholderEmbeddingService`，支持配置驱动的模型切换（OpenAI text-embedding-ada-002 / 本地 bge-large-zh 等）。

```
git add -A && git commit -m "feat: 接入真实 Embedding 模型，支持多模型配置"
```

### S3 — 全文检索接入

接入 Elasticsearch/OpenSearch：文档分块后同步写入 ES 索引，提供关键词+全文检索。

```
git add -A && git commit -m "feat: 接入 Elasticsearch 全文检索"
```

### S4 — 混合检索编排

向量检索 + 全文检索 → 结果合并/rerank，统一为 `SearchService`。

```
git add -A && git commit -m "feat: 混合检索编排（向量 + 全文 → rerank）"
```

---

## 6. 阶段 2D：智能问答（Step4 后半）

### Q1 — QA 相关表 DDL

创建 `qa_conversation`、`qa_message`、`qa_feedback` 表。

```
git add -A && git commit -m "feat: QA 会话/消息/反馈表 DDL"
```

### Q2 — RAG 问答核心流程

`POST /api/ai/qa/ask`：权限过滤 → 混合检索 → 组装 prompt → 调用 LLM → 返回答案+来源引用。

```
git add -A && git commit -m "feat: RAG 智能问答核心流程"
```

### Q3 — 多轮对话

conversationId 关联 + 上下文窗口管理（最近 N 轮对话纳入 prompt）。

```
git add -A && git commit -m "feat: 多轮对话与会话管理"
```

### Q4 — 问答反馈

`POST /api/ai/qa/feedback` 记录用户评价（赞/踩/纠错），供后续优化。

```
git add -A && git commit -m "feat: 问答反馈收集"
```

### Q5 — 安全防护

无来源兜底（"未找到明确依据"）、提示词注入防护、权限过滤确保无权限文档不进入上下文。

```
git add -A && git commit -m "feat: 问答安全防护（兜底+注入防护+权限过滤）"
```

---

## 7. 阶段三：协同业务服务 enterprise-collaboration-service

### 3.1 — 工程骨架初始化

搭建 Spring Boot + MyBatis-Plus 骨架，引入 `frameworks-web-spring-boot-starter`，配置 `application.yml`、`schema.sql`。

```
git add -A && git commit -m "feat(协作): 初始化工程骨架与数据库"
```

### 3.2 — 会议室管理

`meeting_room` 表 CRUD（`/api/meeting/rooms`），含状态管理。

```
git add -A && git commit -m "feat(协作): 会议室管理 CRUD"
```

### 3.3 — 会议创建与冲突检测

`POST /api/meetings`：创建会议 → 检测会议室时间冲突 → 检测参会人时间冲突 → 保存。会议状态：BOOKED/CANCELLED/FINISHED/EXPIRED。

```
git add -A && git commit -m "feat(协作): 会议创建与时间冲突检测"
```

### 3.4 — 会议修改、取消、详情、列表

`PUT /api/meetings/{id}`（修改）、`POST /api/meetings/{id}/cancel`（取消）、`GET /api/meetings/{id}`（详情）、`GET /api/meetings`（列表筛选）。

```
git add -A && git commit -m "feat(协作): 会议修改、取消、查询"
```

### 3.5 — 会议参会人与通知

`meeting_attendee` 管理（邀请/响应），会议创建/修改/取消时触发通知写入 `notification` 表。

```
git add -A && git commit -m "feat(协作): 参会人管理与会议通知"
```

### 3.6 — 会议纪要

`POST /api/meetings/{id}/minutes`（创建纪要）、`GET /api/meetings/{id}/minutes`（查看纪要），含行动项 JSON。

```
git add -A && git commit -m "feat(协作): 会议纪要管理"
```

### 3.7 — 个人待办 CRUD

`todo_item` 全流程：创建（`POST /api/todos`）、修改（`PUT /api/todos/{id}`）、完成（`POST /api/todos/{id}/complete`）、删除（`DELETE /api/todos/{id}`）、列表筛选（`GET /api/todos`）。支持优先级、截止时间、提醒时间、周期性待办。

```
git add -A && git commit -m "feat(协作): 个人待办全流程"
```

### 3.8 — 待办提醒

基于 `reminder_time` 查询到期待办，生成通知记录。

```
git add -A && git commit -m "feat(协作): 待办到期提醒"
```

### 3.9 — 任务创建与分配

`POST /api/tasks`：创建任务、指定负责人（`assignee_id`）、添加参与人（`task_member`）。状态：NOT_STARTED/IN_PROGRESS/WAIT_CONFIRM/DONE/DELAYED/CANCELLED。

```
git add -A && git commit -m "feat(协作): 任务创建与分配"
```

### 3.10 — 任务状态流转

`POST /api/tasks/{id}/status`：状态机驱动流转（NOT_STARTED → IN_PROGRESS → WAIT_CONFIRM → DONE，可回退、可取消、可延期）。

```
git add -A && git commit -m "feat(协作): 任务状态流转"
```

### 3.11 — 任务评论

`POST /api/tasks/{id}/comments`（新增评论）、`GET /api/tasks/{id}/comments`（查看评论列表）。

```
git add -A && git commit -m "feat(协作): 任务评论"
```

### 3.12 — 任务看板

`GET /api/tasks/board`：按状态分组返回任务卡片，支持按负责人/部门/优先级筛选。

```
git add -A && git commit -m "feat(协作): 任务看板"
```

### 3.13 — 消息通知中心

`GET /api/notifications`（通知列表）、`GET /api/notifications/unread-count`（未读计数）、`POST /api/notifications/{id}/read`（标记已读）、`POST /api/notifications/read-all`（全部已读）。支持按通知类型筛选和跳转。

```
git add -A && git commit -m "feat(协作): 消息通知中心"
```

### 3.14 — 基础数据看板

`GET /api/dashboard/knowledge`（知识库指标）、`GET /api/dashboard/meeting`（会议指标）、`GET /api/dashboard/task`（任务指标）、`GET /api/dashboard/personal`（个人指标）。

```
git add -A && git commit -m "feat(协作): 基础数据看板"
```

---

## 8. 阶段四：工作台聚合服务 enterprise-workbench-service

### 4.1 — 工程骨架初始化

搭建 Spring Boot 骨架，引入 `frameworks-web-spring-boot-starter`。

```
git add -A && git commit -m "feat(工作台): 初始化工程骨架"
```

### 4.2 — 聚合接口：今日会议 + 今日待办

`GET /api/workbench/today`：调用协作服务获取当前用户今日会议和待办。

```
git add -A && git commit -m "feat(工作台): 今日会议与待办聚合"
```

### 4.3 — 聚合接口：即将截止任务 + 未读通知 + 常用知识

扩展聚合接口，增加任务截止提醒、未读通知数、知识库快捷入口。

```
git add -A && git commit -m "feat(工作台): 任务提醒、通知、知识入口聚合"
```

### 4.4 — 个人工作统计

`GET /api/workbench/stats`：本周会议时长、任务完成率、知识贡献数。

```
git add -A && git commit -m "feat(工作台): 个人工作统计"
```

---

## 9. 阶段五：联调与权限打通

### 5.1 — 网关路由配置补全

确保所有新接口的网关路由正确转发。

```
git add -A && git commit -m "feat(网关): 补全所有服务路由配置"
```

### 5.2 — 用户身份透传验证

端到端验证：网关 → `X-User-Id`/`X-Is-Admin` 等头 → 下游服务 `UserContextInterceptor` 解析 → 数据权限过滤生效。

```
git add -A && git commit -m "fix: 用户身份透传全链路验证与修复"
```

### 5.3 — 跨服务异常统一处理

协作服务/工作台服务调用知识库服务时的异常传递与统一 `Result` 格式。

```
git add -A && git commit -m "fix: 跨服务异常统一处理"
```

### 5.4 — 操作日志补全

关键操作（会议创建/取消、任务分配/完成、文档上传/删除）落 `operation_log`。

```
git add -A && git commit -m "feat: 补全关键操作审计日志"
```

---

## 10. 阶段六：测试与上线准备

### 6.1 — 单元测试

```
git add -A && git commit -m "test: 补充单元测试"
```

### 6.2 — 接口测试

```
git add -A && git commit -m "test: 补充接口测试"
```

### 6.3 — 权限专项测试

```
git add -A && git commit -m "test: 权限专项测试"
```

### 6.4 — AI 安全测试

```
git add -A && git commit -m "test: 智能问答安全测试"
```

### 6.5 — 部署脚本与监控

```
git add -A && git commit -m "feat: 部署脚本、监控与备份方案"
```

---

## 11. 整体排期

| 阶段 | 周数 | 内容 |
|------|------|------|
| 阶段一 | ✅ 已完成 | 网关、认证、用户、权限 |
| 阶段二-Step3 | ✅ 已完成 | 知识库最小闭环 |
| 阶段 2A | 1 周 | 知识库架构重构（R1-R6） |
| 阶段 2B | 0.5 周 | 数据一致性与容错（C1-C4） |
| 阶段 2C | 1.5 周 | 检索能力（S1-S4） |
| 阶段 2D | 1.5 周 | 智能问答（Q1-Q5） |
| 阶段三 | 3 周 | 协同业务（3.1-3.14） |
| 阶段四 | 1 周 | 工作台聚合（4.1-4.4） |
| 阶段五 | 1 周 | 联调与权限打通（5.1-5.4） |
| 阶段六 | 2 周 | 测试与上线准备（6.1-6.5） |
| **合计剩余** | **11.5 周** | |

---

## 12. 开发优先级

### P0（当前必须）

1. 阶段 2A：架构重构
2. 阶段 2B：一致性修复
3. 阶段 2C：检索能力
4. 阶段三：协同业务（会议、待办、任务、通知）

### P1

1. 阶段 2D：智能问答
2. 阶段四：工作台聚合
3. 阶段五：联调

### P2

1. 阶段 2E 补充功能（如果 2C/2D 超期则后移）
2. 阶段六：正式测试与上线
