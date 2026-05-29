# RAG 权限下推与权限 Metadata 同步设计

## 背景

当前 `rag_qa` 已经完成基础安全修复：

1. 召回后复用 `DocumentVisibilityService` 做 DB 权限终检。
2. 只返回 `SUCCESS` 且启用的文档，以及启用的 chunk。
3. 按 Milvus 召回顺序输出文档和 chunk。
4. 跳过非法 `docId` / `chunkId`，避免单条脏召回导致整个工具失败。

但 RAG 查询仍然先从 Milvus 全库召回，再在 Java 层过滤。这样会产生召回空洞：无权限文档可能占满 topK，过滤后用户有权限的相关文档反而没有机会进入结果。

## 目标

本设计目标是引入混合权限过滤：

1. 在 Milvus `metadata` JSON 中写入权限快照字段。
2. RAG 查询时根据 `UserContext` 生成 Milvus filter，先做粗过滤。
3. Milvus 召回后继续用 DB 权限终检，DB 仍是最终安全边界。
4. 文档权限变更后异步同步 chunk 级 Milvus metadata。
5. 提供管理页面查看同步健康度、失败任务，并支持人工重试。

## 非目标

1. 不引入消息队列。
2. 不做 MySQL + Milvus 分布式事务。
3. 不修改 Milvus collection schema。
4. 不把 chunk 敏感级别纳入本阶段权限 filter。
5. 不做完整 RAG 答案结构化 `{answer, sources}`，该能力后续单独设计。

## 总体方案

采用方案 B：Milvus metadata 粗过滤 + DB 终检 + chunk 级同步任务 + 自动重试 + 管理页面。

权限 metadata 放入现有 Milvus `metadata` JSON 字段，不新增独立标量字段。`PROJECT` / `USER` 授权用数组字段表达。

Milvus filter 是召回质量优化层，不是安全边界。即使 metadata 过期或同步失败，DB 终检也必须阻止无权限内容进入模型上下文。

## 模块边界

### RagPermissionMetadataService

负责从 MySQL 当前文档权限生成 Milvus metadata 扁平字段。

输入：

1. `KbDocument`
2. `KbDocumentPermission` 列表
3. `KbDocumentChunk`

输出：

1. 可合并进 `VectorDocChunk.metadata` 的 `Map<String, Object>`

该服务只生成 metadata，不调用 Milvus，不判断当前用户是否可见。

### RagPermissionFilterBuilder

负责根据 `UserContext` 生成 Milvus filter 表达式。

输入：

1. `UserContext`

输出：

1. Milvus filter 字符串

该服务只做粗过滤表达式生成，不访问 MySQL，不做最终安全判断。

### RagVectorSearchService

封装 RAG 向量检索流程，避免 `RagQaTool` 继续直接编排底层检索。

职责：

1. 选择要查询的 collection。
2. 调用 `RagPermissionFilterBuilder` 生成 filter。
3. 调用 Milvus 检索。
4. 清洗非法 `docId` / `chunkId`。
5. 保持 Milvus 召回顺序。
6. 返回候选 chunk。

### VectorPermissionSyncTaskService

负责 chunk 级权限 metadata 同步任务。

职责：

1. 创建 chunk 级同步任务。
2. 扫描并抢占到期任务。
3. 执行 Milvus metadata upsert。
4. 处理自动重试和 `DEAD` 状态。
5. 支持人工重试。

### VectorPermissionAdminController

提供管理页接口。

职责：

1. 查询同步健康总览。
2. 分页查询任务。
3. 查询任务详情。
4. 单 chunk 任务重试。
5. 按文档重试失败 chunk。

## Milvus Metadata 字段

权限字段采用扁平结构，写入现有 `metadata` JSON：

```json
{
  "doc_id": "10001",
  "chunk_index": 3,
  "permission_type": "PROJECT",
  "owner_id": 2001,
  "department_id": 10,
  "project_ids": [3001, 3002],
  "user_ids": [],
  "admin_only": false,
  "document_status": "SUCCESS",
  "document_enabled": true,
  "chunk_enabled": true
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `permission_type` | string | 文档权限类型：`ALL / DEPARTMENT / PROJECT / USER / ADMIN` |
| `owner_id` | number | 文档 owner |
| `department_id` | number | 文档所属部门 |
| `project_ids` | array<number> | `PROJECT` 授权对象 |
| `user_ids` | array<number> | `USER` 授权对象 |
| `admin_only` | boolean | 是否仅管理员可见 |
| `document_status` | string | 文档状态 |
| `document_enabled` | boolean | 文档是否启用 |
| `chunk_enabled` | boolean | chunk 是否启用 |

## Milvus Filter 语义

普通用户 filter 需要覆盖：

1. owner 可见：`owner_id == userId`
2. 全员可见：`permission_type == "ALL"`
3. 部门可见：`permission_type == "DEPARTMENT" && department_id == user.departmentId`
4. 项目可见：`permission_type == "PROJECT" && project_ids contains user.projectId`
5. 指定用户可见：`permission_type == "USER" && user_ids contains user.userId`
6. 文档和 chunk 可检索：`document_status == "SUCCESS" && document_enabled == true && chunk_enabled == true`

管理员 filter 需要额外覆盖：

1. `permission_type == "ADMIN"`
2. `admin_only == true`

Milvus JSON 数组 contains 表达式需要在实现阶段用当前 SDK 和 Milvus 版本验证。如果数组表达式不可用，第一版可降级为只下推 owner、ALL、DEPARTMENT、ADMIN、状态字段，`PROJECT` / `USER` 继续依赖 DB 终检，但必须在实现计划中显式验证并记录结果。

## 数据模型

新增表：`kb_vector_permission_sync_task`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `kb_id` | bigint | 知识库 ID |
| `document_id` | bigint | 文档 ID |
| `chunk_id` | bigint | chunk ID |
| `collection_name` | varchar | Milvus collection |
| `status` | varchar | `PENDING / RUNNING / SUCCESS / FAILED / DEAD` |
| `retry_count` | int | 已自动重试次数 |
| `max_retry` | int | 最大自动重试次数 |
| `next_retry_at` | datetime | 下次可自动重试时间 |
| `last_error` | text | 最近失败原因 |
| `skip_reason` | varchar | 跳过原因，删除数据等场景使用 |
| `trigger_type` | varchar | 触发类型 |
| `created_by` | bigint | 创建人 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |
| `started_at` | datetime | 最近开始时间 |
| `finished_at` | datetime | 最近结束时间 |
| `deleted` | tinyint | 逻辑删除 |

`trigger_type` 取值：

1. `DOCUMENT_PERMISSION_CHANGED`
2. `DOCUMENT_ENABLED_CHANGED`
3. `CHUNK_ENABLED_CHANGED`
4. `MANUAL_RETRY`
5. `REBUILD`

索引：

1. `(status, next_retry_at)`：后台扫描到期任务。
2. `(document_id, status)`：按文档查看和批量重试。
3. `(kb_id, status, updated_at)`：管理页统计。
4. `(chunk_id)`：查重和定位 chunk。

任务去重规则：

1. 同一 chunk 已有 `PENDING` 或 `RUNNING` 任务时，不重复创建。
2. 同一 chunk 已有 `FAILED` 或 `DEAD` 任务时，新的权限变更复用并重置该任务。
3. 复用任务时更新 `trigger_type`、`retry_count`、`next_retry_at`、`last_error`、`skip_reason`。

## 同步流程

### 新写入向量

文档分块或手动新增 chunk 时：

1. 读取当前文档和权限数据。
2. `RagPermissionMetadataService` 生成权限 metadata。
3. 合并到 `VectorDocChunk.metadata`。
4. 随向量一起写入 Milvus。

该路径不创建同步任务，除非向量写入失败且现有业务语义要求补偿。

### 文档权限变更

1. MySQL 权限变更在本地事务中完成。
2. 事务提交后发布或处理 `DocumentPermissionChangedEvent`。
3. 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 创建 chunk 级同步任务。
4. 后台 worker 异步同步 Milvus metadata。

### 自动同步 Worker

1. 定时扫描 `PENDING` 且 `next_retry_at <= now` 的任务。
2. 抢占任务：原子更新 `PENDING -> RUNNING`。
3. 查询 document、permission、chunk 当前数据。
4. 数据已删除或不存在时，置为 `SUCCESS` 并写 `skip_reason`。
5. 生成最新 metadata。
6. 调用 Milvus upsert 更新该 chunk 的 metadata。
7. 成功置为 `SUCCESS`。
8. 失败时按指数退避进入 `FAILED`，超过最大次数进入 `DEAD`。

### 人工重试

管理页面支持：

1. 单 chunk 重试。
2. 按文档重试该文档失败和 `DEAD` chunk。

人工重试将任务改回 `PENDING`，设置 `next_retry_at = now`。Milvus 更新仍由 worker 统一执行。

## 重试策略

默认自动重试：

1. 最大自动重试次数：4
2. 延迟序列：1 分钟、5 分钟、15 分钟、60 分钟
3. 超过后进入 `DEAD`

人工重试：

1. 可重试 `FAILED` 和 `DEAD`
2. 人工重试不直接调用 Milvus
3. 人工重试重置为 `PENDING`

## 事务边界

### 新写入向量

MySQL 文档、chunk、权限数据用本地事务保证一致。Milvus 写入不是 MySQL 事务的一部分，不做分布式事务。

如果 Milvus 写入失败，本设计不改变现有分块链路语义。当前链路若将文档或分块任务标记为失败，仍按原逻辑处理。权限 metadata 同步任务只负责权限快照更新，不接管初始向量写入失败补偿。

### 权限变更

权限变更必须先提交 MySQL 事务。只有事务提交成功后，才创建权限 metadata 同步任务。

MySQL 是权限真相源。Milvus metadata 是检索优化副本。

## 管理接口

接口放在知识服务管理路径下。

### 总览

`GET /api/kb/admin/vector-permission-sync/overview`

返回：

1. `pendingCount`
2. `runningCount`
3. `failedCount`
4. `deadCount`
5. `successRate`
6. `lastSyncedAt`

### 任务分页

`GET /api/kb/admin/vector-permission-sync/tasks`

筛选参数：

1. `kbId`
2. `documentId`
3. `status`
4. `collectionName`
5. `updatedAtStart`
6. `updatedAtEnd`
7. `current`
8. `size`

### 任务详情

`GET /api/kb/admin/vector-permission-sync/tasks/{taskId}`

返回任务详情、错误信息、重试次数、chunk 信息、metadata 摘要。

### 单任务重试

`POST /api/kb/admin/vector-permission-sync/tasks/{taskId}/retry`

将 `FAILED` 或 `DEAD` 任务重置为 `PENDING`。

### 按文档重试

`POST /api/kb/admin/vector-permission-sync/documents/{documentId}/retry`

重试该文档下失败和 `DEAD` 的 chunk 任务。

## 管理页面

页面采用“总览 + 任务表”结构。

顶部健康总览：

1. 待处理
2. 运行中
3. 失败
4. `DEAD`
5. 成功率
6. 最近同步时间

中间筛选栏：

1. 知识库
2. 文档 ID
3. 状态
4. collection
5. 更新时间范围

下方任务表：

1. 文档标题
2. chunk ID
3. collection
4. 状态
5. retry count
6. next retry time
7. last error
8. 操作：详情、单 chunk 重试

批量操作：

1. 按文档重试失败 chunk

## 管理权限

1. 系统管理员可查看和操作全部任务。
2. 知识库管理员或知识库 owner 只能查看和操作自己管理知识库下的任务。
3. 普通文档 owner 本阶段不能访问该管理页面。
4. 后端接口必须做同样的数据范围过滤，不能只依赖前端隐藏。

## 错误处理

1. Milvus 调用失败：任务进入 `FAILED`，记录 `last_error`，计算 `next_retry_at`。
2. 超过最大自动重试：任务进入 `DEAD`。
3. 文档或 chunk 已删除：任务进入 `SUCCESS`，写 `skip_reason`。
4. collection 不存在：任务进入 `FAILED`，等待自动重试或人工处理。
5. metadata 生成失败：任务进入 `FAILED`，记录异常。

## 测试策略

### Metadata 生成单测

覆盖：

1. `ALL`
2. `DEPARTMENT`
3. `PROJECT`
4. `USER`
5. `ADMIN`
6. owner 字段
7. 文档和 chunk 状态字段

### Filter 生成单测

覆盖不同 `UserContext`：

1. owner 可见
2. ALL 可见
3. department 匹配
4. project_ids 包含
5. user_ids 包含
6. admin 可见 ADMIN
7. 状态和 enabled 条件始终存在

### RAG 查询单测

覆盖：

1. 带 filter 调 Milvus。
2. 清洗坏 ID。
3. 保留 DB 终检。
4. 不返回未授权内容。
5. 保持 Milvus 召回顺序。

### 任务状态机单测

覆盖：

1. 创建 chunk 任务。
2. 自动重试。
3. 超过次数变 `DEAD`。
4. 人工重试回到 `PENDING`。
5. 删除数据写 `skip_reason`。

### 管理接口测试

覆盖：

1. 系统管理员可见全部。
2. 知识库管理员仅可见管理范围。
3. 单任务重试权限。
4. 按文档重试权限。

### Milvus Filter 兼容测试

Milvus JSON 数组 filter 兼容性作为可选 integration profile。日常单测 mock Milvus，不强依赖本地 Milvus。

## 落地顺序

### 阶段 1：权限 metadata 生成

实现 `RagPermissionMetadataService`，让新写入向量时 metadata 带权限字段。暂时不启用 Milvus filter。

### 阶段 2：RAG filter 下推

实现 `RagPermissionFilterBuilder` 和 `RagVectorSearchService`。RAG 查询带权限 filter，同时保留 DB 终检。

### 阶段 3：权限变更任务表

新增 `kb_vector_permission_sync_task`。权限变更后创建 chunk 级同步任务。先实现任务创建、查询和去重。

### 阶段 4：自动同步 worker

实现状态机、指数退避、自动重试、人工重试。

### 阶段 5：管理页面

实现总览、筛选、任务表、单 chunk 重试、按文档重试。

## 验收标准

1. 新写入向量的 Milvus metadata 包含权限字段。
2. RAG 查询会带权限 filter 调 Milvus。
3. RAG 查询后仍执行 DB 权限终检。
4. 权限变更后会生成 chunk 级同步任务。
5. 同步失败会自动重试，超过次数进入 `DEAD`。
6. 管理页面可查看总览和任务列表。
7. 系统管理员和知识库管理员的数据范围隔离正确。
8. 目标单测覆盖 metadata、filter、RAG 查询、状态机和管理接口。
