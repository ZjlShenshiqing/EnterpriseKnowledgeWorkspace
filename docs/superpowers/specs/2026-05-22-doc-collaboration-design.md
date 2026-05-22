# 文档协作 — 实时协同编辑设计

## 概述

将当前"单人 CRUD + 富文本编辑器"的文档功能升级为完整协同文档体验：多人实时编辑 + 光标同步 + 评论 + 分享权限。

## 技术选型

- **协同算法**: OT（Operational Transformation），ShareDB 兼容协议
- **编辑器**: Quill（Delta 模型，与 ShareDB 集成最成熟）
- **传输**: 独立 WebSocket `/ws/docs`，不混用 `/ws/chat`
- **存储**: 最终内容 + 操作日志双存，每 50 版本快照一次

## 架构

```
浏览器 (Vue 3 + Quill + sharedb-client)
  ├─ Quill 编辑器
  ├─ sharedb-client (OT 协同)
  └─ quill-cursors (光标同步)
       │ HTTP REST       │ WebSocket /ws/docs
       ▼                 ▼
enterprise-gateway-service (:8086)
  路由 /api/docs/** → collaboration
  路由 /ws/docs → collaboration (WebSocket 透传)
       │
       ▼
enterprise-collaboration-service (:8082)
  REST Controllers:
   ├─ DocController (改造) — 文档 CRUD
   ├─ DocCommentController (新增) — 评论
   ├─ DocShareController (新增) — 分享链接
   └─ DocCollaboratorController (新增) — 协作者管理
  WebSocket:
   └─ DocWebSocketHandler — OT 协议处理
  Services:
   ├─ DocOTService — OT 变换引擎
   └─ DocPresenceService — 在线状态/光标广播
```

## OT 引擎设计

### 处理流程

```
客户端 A 提交 op (baseVersion=5) ──┐
                                    ├─▶ OT 引擎 ──▶ 变换后 op ──▶ 写入 DB
客户端 B 提交 op (baseVersion=5) ──┘                   广播给所有订阅者
```

1. 收到客户端 op，比较 baseVersion 和文档当前 version
2. baseVersion == currentVersion：直接应用，版本 +1，广播
3. baseVersion < currentVersion：取出历史 ops 做 OT 变换，应用后广播
4. baseVersion > currentVersion：拒绝

### 并发控制

- 同一文档操作通过 per-docId 的 ReentrantLock 串行化
- 不同文档互不阻塞
- 操作日志异步批量写入 sys_doc_operation

### 快照策略

- 每 50 个版本生成快照，写入 sys_doc.content（Quill Delta JSON）
- sys_doc.snapshot_version 记录快照版本号
- 文档加载 = 最新快照 + 重放快照后 ops

## WebSocket 协议 (`/ws/docs`)

```json
// 客户端 → 服务端
{ "action": "sub", "docId": "xxx" }
{ "action": "op", "docId": "xxx", "ops": [...], "version": 5 }
{ "action": "cursor", "docId": "xxx", "range": {...} }
{ "action": "presence", "docId": "xxx", "online": true }

// 服务端 → 客户端
{ "action": "op", "docId": "xxx", "ops": [...], "version": 6, "userId": "..." }
{ "action": "ack", "docId": "xxx", "version": 6 }
{ "action": "cursor", "docId": "xxx", "userId": "...", "range": {...} }
{ "action": "presence", "docId": "xxx", "userId": "...", "online": true/false }
```

## API 设计

### 文档 CRUD（改造）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/docs` | 文档列表，keyword 搜索 |
| GET | `/api/docs/{id}` | 文档详情，返回 Delta + version |
| POST | `/api/docs` | 创建，初始化空 Delta |
| PUT | `/api/docs/{id}` | 更新标题等元信息（内容走 OT） |
| DELETE | `/api/docs/{id}` | 逻辑删除 |

### 评论

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/docs/{id}/comments` | 评论列表 |
| POST | `/api/docs/{id}/comments` | 添加评论 {content, anchorIndex?, anchorLength?, parentId?} |
| PUT | `/api/comments/{id}` | 编辑或标记解决 |
| DELETE | `/api/comments/{id}` | 删除评论 |

### 协作者

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/docs/{id}/collaborators` | 协作者列表（含在线状态） |
| POST | `/api/docs/{id}/collaborators` | 添加 {targetType: USER/DEPT, targetId, permission} |
| PUT | `/api/collaborators/{id}` | 修改权限 |
| DELETE | `/api/collaborators/{id}` | 移除 |

### 分享链接

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/docs/{id}/shares` | 分享链接列表 |
| POST | `/api/docs/{id}/shares` | 创建 {permission, expireAt?} |
| DELETE | `/api/shares/{id}` | 撤销链接 |
| GET | `/api/docs/shared/{token}` | 通过 token 打开文档 |

## 数据库变更

### 新增表

**sys_doc_operation** — 操作日志

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | PK |
| doc_id | BIGINT | 文档 ID |
| user_id | BIGINT | 操作人 |
| version | INT | 操作后版本号 |
| operation | LONGTEXT | Quill Delta JSON |
| created_at | DATETIME | |

**sys_doc_comment** — 评论

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | PK |
| doc_id | BIGINT | |
| user_id | BIGINT | 评论人 |
| content | TEXT | |
| anchor_index | INT | 锚定起始位置（可空） |
| anchor_length | INT | 锚定长度 |
| parent_id | BIGINT | 回复目标评论 ID |
| resolved | TINYINT | 0/1 |
| deleted | TINYINT | |
| created_at | DATETIME | |
| updated_at | DATETIME | |

**sys_doc_share_link** — 分享链接

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | PK |
| doc_id | BIGINT | |
| token | VARCHAR(64) | 唯一索引 |
| permission | VARCHAR(10) | VIEW/COMMENT/EDIT |
| expired_at | DATETIME | 可空=永久 |
| deleted | TINYINT | |
| created_at | DATETIME | |

**sys_doc_collaborator** — 协作者

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | PK |
| doc_id | BIGINT | |
| target_type | VARCHAR(10) | USER/DEPT |
| target_id | BIGINT | |
| permission | VARCHAR(10) | VIEW/COMMENT/EDIT |
| deleted | TINYINT | |
| created_at | DATETIME | |

### 改造现有表

**sys_doc 新增字段：**

| 列 | 类型 | 说明 |
|---|---|---|
| version | INT | 当前版本号，默认 0 |
| snapshot_version | INT | 最后快照版本号，默认 0 |

## 前端改造

- contenteditable → Quill 编辑器
- 集成 sharedb-client 连接 /ws/docs
- quill-cursors 插件实现光标同步
- 右侧面板全部接入真实 API（评论、协作者、分享链接）

## 实施阶段

| 阶段 | 内容 |
|------|------|
| 1 | OT 引擎 + WebSocket + 数据库变更（后端核心） |
| 2 | 前端 Quill 迁移 + ShareDB 客户端 + 光标同步 |
| 3 | 评论系统（后端 API + 前端面板） |
| 4 | 分享权限（分享链接 + 协作者管理） |

## 权限模型

文档访问权限判定优先级：

1. 文档创建者 → EDIT
2. 协作者表匹配（user_id 或 dept_id）→ 对应权限
3. 有效分享链接 token → 对应权限
4. 以上都不满足 → 拒绝访问
