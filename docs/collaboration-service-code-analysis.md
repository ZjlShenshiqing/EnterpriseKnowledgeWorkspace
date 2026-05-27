# enterprise-collaboration-service 服务分析

> **文档版本**：v3.0 · **更新日期**：2026-05-25  
> 基于当前 `enterprise-collaboration-service` 源码整理，覆盖 IM、协同文档 OT、会议/任务/审批、意图树、关键词映射、WebSocket 与外部集成。

本文重点解释协同服务的 **7 条能力线**、双认证体系、WebSocket 协议、OT 算法要点，以及与 Gateway / Workbench / Knowledge Agent 的集成边界。

**文档结构**

| 章节 | 内容 |
|------|------|
| §1–§4 | 定位、架构、目录树、启动配置 |
| §5–§6 | 鉴权模型、Controller 分组 |
| §7–§12 | IM、文档 OT、会议、办公流、意图/关键词 |
| §13–§14 | 数据库与迁移、外部集成 |
| §15–§16 | 前端消费、响应与错误 |
| §17–§18 | 阅读路线、逐文件代码地图 |
| §19–§21 | 附录：REST 速查、WebSocket 协议、调用链、Gotchas |
| §22–§28 | **v3 新增**：实体字段全表、Service 方法索引、逐 Controller 请求/响应、前端映射、DDL 摘录、87 文件清单 |

---

## 1. 服务定位

`enterprise-collaboration-service` 是企业 **协同中台**，承担 7 条能力线：

| # | 能力线 | 说明 |
|---|--------|------|
| 1 | **即时通讯 IM** | 会话、消息、已读、OSS 文件、RocketMQ、WebSocket 推送 |
| 2 | **协同文档** | Quill Delta OT、WebSocket 实时同步、评论/协作者/分享 |
| 3 | **会议** | CRUD、冲突检测、Zoom 可选、Workbench 缓存失效 |
| 4 | **办公流** | 待办、任务看板、多级审批、公告 |
| 5 | **意图树** | 节点/规则/知识库绑定 + match 测试 |
| 6 | **关键词映射** | utterance → 意图/KB 关键词命中 |
| 7 | **独立认证** | 协同库 `sys_user` + 自有 JWT（WebSocket/直连） |

| 项 | 值 |
|---|---|
| 端口 | **8090** |
| 数据库 | MySQL `enterprise_collaboration` |
| ORM | MyBatis-Plus 3.5.7 |
| WebSocket | `/ws/chat`、`/ws/docs`（**不经 Gateway**） |
| MQ | RocketMQ topic `im-message` |
| 运行时 | **Spring MVC（Servlet）** + WebSocket |
| Nacos 名 | `enterprise-collaboration-service` |
| Java 源文件 | **87** 个 |

**技术栈**：Spring Boot 3.4.4、MyBatis-Plus、Spring WebSocket、RocketMQ 2.3.2、AWS S3 SDK（OSS 兼容）、jjwt 0.12.6、Nacos、`frameworks-web-spring-boot-starter`

### 1.1 平台架构位置

```text
                    ┌──────────────── Gateway :8086 ────────────────┐
                    │  /api/meetings|todos|tasks|chat|docs|...      │
                    ▼                                              │
┌──────── enterprise-web :5173 ──────┐                            │
│  REST → Gateway（多数 /api/*）      │                            │
│  WS   → 直连 :8090/ws/chat|docs    │                            │
└────────────────────────────────────┘                            │
                    │                                              │
                    ▼                                              ▼
         enterprise-collaboration-service :8090
                    │
        ┌───────────┼───────────┬──────────────┐
        ▼           ▼           ▼              ▼
   MySQL       RocketMQ      OSS          HTTP → Workbench :8084
 enterprise_   im-message   im-files      (cache evict)
 collaboration
```

**与 Gateway 的分工**：Gateway 负责 JWT + 路由；Collaboration 负责业务数据。Gateway 的 `sys_user`（`enterprise_gateway`）与协同的 `sys_user`（`enterprise_collaboration`）**完全独立**。

**与 Knowledge 的集成**：`enterprise-knowledge-ai-service` 的 `CollaborationClient` 直连 `:8090` 调用会议 API（Agent MCP Tool）。

---

## 2. 代码结构树

```text
enterprise-collaboration-service/src/main/java/com/zjl/collaboration/
├── CollaborationApplication.java
├── config/
│   ├── FilterConfig.java              # JwtAuthFilter 注册 /api/*
│   ├── WebSocketConfig.java           # /ws/chat, /ws/docs
│   ├── MybatisPlusConfig.java
│   └── ImOssProperties.java           # app.im.oss.*
├── web/                               # 13 Controller + 2 WS + Filter
│   ├── AuthController.java
│   ├── MeetingController.java
│   ├── TodoController / TaskController / ApprovalController
│   ├── AnnouncementController / ContactController
│   ├── ChatController.java
│   ├── DocController / DocCommentController / DocShareController
│   ├── IntentController / KeywordMappingController
│   ├── ChatWebSocketHandler.java
│   ├── DocWebSocketHandler.java
│   ├── JwtAuthFilter.java
│   └── MutableRequestWrapper.java
├── service/
│   ├── DocOTService.java              # Quill Delta OT + 版本锁
│   ├── DocPermissionService.java
│   ├── DocPresenceService.java
│   ├── IntentService.java
│   ├── ImMessageService.java
│   ├── ImMessageConsumer.java         # RocketMQ + onlineUsers Map
│   ├── ImReadService.java
│   ├── ImFileService.java
│   ├── UserLoginService.java
│   └── impl/UserLoginServiceImpl.java
├── integration/
│   ├── WorkbenchCacheNotifier.java
│   └── ZoomMeetingClient.java
├── dto/                               # 登录/注册 DTO
├── entity/                            # ~25 实体
├── mapper/                            # ~25 Mapper
└── util/
    ├── JwtUtil.java                   # 协同 JWT（auth.jwt.*）
    └── JwtClaimsSupport.java          # userId claim 或 sub 兼容
```

---

## 3. 启动与配置

### 3.1 启动入口

```java
@EnableCaching
@SpringBootApplication(scanBasePackages = {"com.zjl.collaboration", "com.zjl.common"})
@MapperScan("com.zjl.collaboration.mapper")
public class CollaborationApplication { ... }
```

### 3.2 application.yml 要点

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | **8090** | |
| `auth.jwt.expiration` | **86400000** ms（24h） | |
| `auth.jwt.secret` | Nacos/secrets | **application.yml 未写**，须外部配置 |
| `rocketmq.name-server` | localhost:9876 | |
| `rocketmq.producer.group` | im-producer-group | |
| `app.workbench.service-url` | http://localhost:8084 | 缓存失效回调 |
| `app.im.oss.*` | endpoint/keys/bucket | IM 文件上传 |
| `flyway.enabled` | **false** | 迁移手工执行 |
| `spring.sql.init.mode` | **never** | |
| `spring.autoconfigure.exclude` | FlywayAutoConfiguration | |

### 3.3 Maven 依赖摘要

| 依赖 | 用途 |
|------|------|
| frameworks-web-spring-boot-starter | Result、GlobalExceptionHandler、TraceId |
| mybatis-plus-spring-boot3-starter | ORM |
| spring-boot-starter-websocket | WS |
| rocketmq-spring-boot-starter | IM 异步 |
| software.amazon.awssdk:s3 | OSS 兼容上传 |
| jjwt | 协同 JWT |
| spring-boot-starter-data-redis | 依赖引入（@EnableCaching） |
| nacos discovery/config | 注册与配置 |

### 3.4 Gateway 路由前缀（经 :8086）

```
/api/meetings/**  /api/todos/**  /api/tasks/**  /api/notifications/**
/api/chat/**  /api/docs/**  /api/approvals/**  /api/announcements/**
/api/intents/**  /api/keyword-mappings/**
```

**不经 Gateway**：`/ws/chat`、`/ws/docs`、开发期若直连 `:8090` 的全部路径。

---

## 4. 鉴权模型（核心）

### 4.1 JwtAuthFilter 决策树

**注册**：`FilterConfig` · URL `/api/*` · Order **1**

```text
请求 /api/*
  │
  ├─ path startsWith /api/auth/login → 放行
  │
  ├─ Header 含 X-User-Id（非空）→ 放行（Gateway 已鉴权）
  │
  ├─ Authorization: Bearer <token>
  │     → collaboration JwtUtil.parse
  │     → MutableRequestWrapper 注入:
  │         X-User-Id = claims.userId   ⚠ 不读 sub
  │         X-Department-Id = claims.deptId
  │         X-Is-Admin = claims.isAdmin
  │
  └─ 否则 → 放行（无身份头，Controller 自行处理或空 userId）
```

**与 Gateway JWT 的差异**：

| 项 | Gateway JWT | Collaboration JWT |
|----|-------------|-------------------|
| userId | `sub` | `userId` claim |
| 密钥 | `app.security.jwt.secret` | `auth.jwt.secret` |
| 黑名单 | DB `sys_token_blacklist` | 内存 `ConcurrentHashMap` |

经 Gateway 的 REST：**推荐**依赖 `X-User-Id`，不要指望 Bearer 在 Filter 里从 Gateway token 解析（Filter 只读 `userId` claim，不读 `sub`）。

### 4.2 WebSocket 鉴权

**Chat** 与 **Doc** WS 均用 query：`?token=<JWT>`

```java
// ChatWebSocketHandler / DocWebSocketHandler
Claims c = jwtUtil.parse(token);  // 协同 JwtUtil
Long userId = JwtClaimsSupport.resolveUserId(c);  // userId 或 sub ✅
```

前端 `Chats.vue` / `Documents.vue` 传的是 `localStorage.token`（**Gateway 登录 token**）。  
WS 能连上需满足：**两服务 JWT secret 一致**，且 `JwtClaimsSupport` 从 `sub` 解析 userId。

CloseStatus：`4001` Token 过期 · `4002` Token 无效

### 4.3 协同自有登录（AuthController）

独立账号体系，供 IM/WS 或直连协同使用：

| API | 说明 |
|-----|------|
| POST `/api/auth/login` | 返回 `accessToken`（协同 JWT） |
| GET `/api/auth/check-login?accessToken=` | 校验 |
| POST `/api/auth/logout?accessToken=` | 内存黑名单 |
| POST `/api/auth/register` | 注册 |
| POST `/api/auth/deletion` | 物理删用户 |
| GET `/api/auth/has-username?username=` | 用户名是否存在 |

`UserLoginServiceImpl` 签发 claims：`userId`, `username`, `isAdmin`（及 register 时 deptId 等）。

**DTO 字段**：

| DTO | 字段 |
|-----|------|
| UserLoginReqDTO | username, password |
| UserLoginRespDTO | accessToken, userId, username, realName, isAdmin |
| UserRegisterReqDTO | username, password, realName?, deptId? |
| UserRegisterRespDTO | userId, username |
| UserDeletionReqDTO | username, password |

密码存储：`UserLoginServiceImpl` 使用 BCrypt 校验/编码。

**Filter 白名单范围**：仅 `/api/auth/login` 前缀；register/logout 等仍走 Filter，但无 Bearer 且无 X-User-Id 时 **也会放行**（Filter 最后一支 `chain.doFilter` 无身份）。

---

## 5. Controller 分组总览

| Controller | 前缀 | 身份 | 核心职责 |
|-----------|------|------|----------|
| AuthController | `/api/auth` | 部分白名单 | 协同登录/register |
| MeetingController | `/api/meetings` | X-User-Id | 会议 CRUD、冲突检测、Zoom |
| TodoController | `/api/todos` | X-User-Id | 个人待办 |
| TaskController | `/api/tasks` | X-User-Id（创建） | 任务看板+评论 |
| ApprovalController | `/api/approvals` | X-User-Id, X-Is-Admin | 审批流 |
| AnnouncementController | `/api/announcements` | X-User-Id | 公告 |
| ContactController | `/api/contacts` | — | 部门/用户（协同库） |
| ChatController | `/api/chat` | X-User-Id | IM REST |
| DocController | `/api/docs` | X-User-Id（创建） | 协同文档 CRUD |
| DocCommentController | `/api/docs/.../comments` | — | 评论 |
| DocShareController | `/api/docs/...` | — | 协作者、分享链接 |
| IntentController | `/api/intents` | — | 意图树管理 + match |
| KeywordMappingController | `/api/keyword-mappings` | — | 关键词 CRUD + match |

---

## 6. 即时通讯（IM）

### 6.1 数据模型

| 表 | 用途 |
|----|------|
| `im_conversation` | 会话（type: private/group 等） |
| `im_conversation_member` | 成员 |
| `im_message` | 消息体、status、mqMsgId |
| `im_message_read` | 每用户每会话 lastReadMsgId |
| `im_message_file` | 文件消息附件 |

### 6.2 REST API（ChatController）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/conversations` | 当前用户会话，含 unread、last_msg |
| GET | `/unread-count` | 全会话未读总和（Workbench 用） |
| GET | `/messages/{convId}?page&size` | 分页，**时间升序**返回 |
| POST | `/conversations` | 创建；private 双人去重 |
| POST | `/conversations/{id}/read` | body: `{lastReadMsgId}` |
| POST | `/files/upload` | multipart → OSS |
| GET | `/members/{convId}` | 成员列表 |

**私聊去重逻辑**：创建 private 且仅 1 个 memberId 时，若已存在仅含两人的 private 会话则返回已有 id。

### 6.3 消息发送链路

```text
【WebSocket 发消息】
Client WS JSON: { conversationId, content, clientMsgId }
  → ChatWebSocketHandler.handleTextMessage
  → ImMessageService.send
       1. INSERT im_message (status=SENT)
       2. UPDATE im_conversation last_msg_*
       3. 同步推送给 onlineUsers 中会话成员
       4. asyncSend RocketMQ topic "im-message"
       5. 返回 ack { type, clientMsgId, serverMsgId, status }
  → WS 回 ack 给发送者

【RocketMQ 消费】
ImMessageConsumer.onMessage
  → 再次更新 message/conversation（幂等倾向）
  → 推送给 onlineUsers（与步骤 3 可能重复推送）
```

**onlineUsers**：`ImMessageConsumer.onlineUsers` 静态 `ConcurrentHashMap<Long, WebSocketSession>`，Chat WS 连接时 put，断开 remove。

### 6.4 已读（ImReadService）

- `markRead`：upsert `im_message_read.last_read_msg_id`
- 向会话内**其他发送者** WS 推送 `{type:"read", conversationId, userId, lastReadMsgId}`
- `unreadCount`：`message.id > lastReadId` 的 count

### 6.5 Chat WebSocket 协议

**连接**：`ws://host:8090/ws/chat?token=JWT`

**客户端 → 服务端**（JSON）：

```json
{ "conversationId": 1, "content": "hello", "clientMsgId": "uuid-local" }
```

**服务端 → 客户端**：

| type | 含义 |
|------|------|
| `ack` | 发送确认，含 serverMsgId |
| `message` | 新消息广播 |
| `status` | 用户 online/offline |
| `read` | 已读回执 |

### 6.6 RocketMQ 配置

```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: im-producer-group
```

Consumer：`@RocketMQMessageListener(topic = "im-message", consumerGroup = "im-consumer-group")`

MQ 不可用：`ImMessageService` catch 后 warn，**消息仍已入库**，在线推送仍执行。

### 6.7 IM 文件（ImFileService + ImOssProperties）

| 配置键 | 说明 |
|--------|------|
| `app.im.oss.endpoint` | S3 兼容 endpoint（MinIO/阿里云 OSS 等） |
| `app.im.oss.region` | AWS Region 字符串 |
| `app.im.oss.access-key` / `secret-key` | 为空则 `s3Client=null`，上传抛 `SYSTEM_ERROR` |
| `app.im.oss.bucket` | 存储桶名 |

**上传流程**（`POST /api/chat/files/upload`）：

```text
MultipartFile
  → 校验文件名不含 ".."
  → key = "im/" + UUID + "/" + originalName
  → S3 PutObject
  → 返回 { ossKey, fileName, fileSize, fileType }
```

**下载缺口**：前端 `Chats.vue` 使用 `GET /api/chat/files/{ossKey}` 展示/下载图片，但 **`ChatController` 当前仅实现 upload，无 GET 下载端点**（设计文档 `im-redesign-design.md` 有规划）。生产需补 Controller 代理 `ImFileService.read(ossKey)` 或改为 OSS 直链。

### 6.8 ChatWebSocketHandler 要点

与 Doc WS 类似：`afterConnectionEstablished` 解析 `?token=`，`JwtClaimsSupport.resolveUserId`。  
连接成功后注册 `ImMessageConsumer.onlineUsers.put(userId, session)`；断开 remove。  
`handleTextMessage` 解析 JSON → `ImMessageService.send(senderId, senderName, conversationId, content, clientMsgId)` → 回写 ack TextMessage。

---

## 7. 协同文档

### 7.1 数据模型

| 表 | 用途 |
|----|------|
| `sys_doc` | title, content(Quill Delta JSON), version, snapshot_version |
| `sys_doc_operation` | 每次 OT op 记录 |
| `sys_doc_comment` | 评论 |
| `sys_doc_collaborator` | USER/DEPT 协作者 + permission |
| `sys_doc_share_link` | token 分享链接 |

新建文档默认 content：

```json
{"ops":[{"insert":"\n"}]}
```

### 7.2 REST（DocController）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/docs?keyword&page&size` | 分页列表（无 content） |
| GET | `/api/docs/{id}` | 含 content + version（经 DocOTService.getDocument） |
| POST | `/api/docs` | 创建，updatedBy=userId |
| PUT | `/api/docs/{id}` | 仅改 title |
| DELETE | `/api/docs/{id}` | 物理删除 |

### 7.3 OT 算法（DocOTService）

| 常量/机制 | 值 |
|-----------|-----|
| `SNAPSHOT_INTERVAL` | **50** 次 op 写 content 快照 |
| 锁 | 每 docId 一个 `ReentrantLock`（内存） |
| 冲突 | `baseVersion < currentVersion` → 加载中间 ops 做 transform |
| 算法 | Quill Delta OT（insert/delete/retain） |

**submitOperation 流程**：

```text
lock(docId)
  currentVersion = doc.version
  if baseVersion > currentVersion → throw
  if baseVersion < currentVersion → transform against concurrent ops
  INSERT sys_doc_operation(version+1)
  doc.version++
  if version - snapshotVersion >= 50 → applyOpsToContent + snapshotVersion=version
  UPDATE sys_doc
unlock
```

### 7.4 Doc WebSocket 协议

**连接**：`ws://host:8090/ws/docs?token=JWT`

**action 类型**：

| action | 方向 | 说明 |
|--------|------|------|
| `sub` | C→S | 订阅 docId；返回 `init`含 content/version/permission |
| `op` | C→S | 提交 `{docId, version, ops[]}` |
| `ack` | S→C | 本端确认 version+1 |
| `op` | S→其他 | 广播变换后 ops |
| `cursor` | 双向 | 光标 range |
| `presence` | 双向 | 在线状态 |
| `error` | S→C | `{action:"error", message}` |

**权限**（DocPermissionService）：创建者 EDIT > USER 协作者 > DEPT 协作者 > null。  
WS `sub` 时 `checkPermission(docId, userId, null)` — **deptId 传 null**，部门级协作在 WS 场景可能失效。

### 7.5 分享与协作者（DocShareController）

- 协作者 CRUD：`/api/docs/{docId}/collaborators`
- 分享链接：`/api/docs/{docId}/shares` · 公开访问 `GET /api/docs/shared/{token}`
- `checkShareToken`：校验 expiredAt

### 7.6 DocWebSocketHandler 方法级说明

| 方法 | 触发 | 行为 |
|------|------|------|
| `afterConnectionEstablished` | WS 握手 | 解析 token → sessionUsers 存 UserContext(userId,userName) |
| `handleSubscribe` | action=sub | checkPermission → presence.join → trackSubscription → init 消息 |
| `handleOperation` | action=op | permission≥EDIT → submitOperation → ack(version+1) → 广播 op 给其他订阅者 |
| `handleCursor` | action=cursor | 广播 range（排除自身 session） |
| `handlePresence` | action=presence | broadcastPresence(online 布尔) |
| `afterConnectionClosed` | 断开 | removeSession → 对各 docId leave + offline presence |

**OT 失败**：catch 后 `sendError("操作冲突，请刷新页面")`，不自动重试 transform。

**权限与 op**：`handleOperation` 内再次校验 EDIT；只读协作者 sub 成功但 op 会被拒。

### 7.7 DocPresenceService 内存模型

```text
docSessions: ConcurrentHashMap<docId, ConcurrentHashMap<sessionId, SessionInfo>>
sessionDocs: ConcurrentHashMap<sessionId, Set<docId>>  // 断开时批量 leave

SessionInfo = (userId, userName, WebSocketSession)
```

单 JVM 内有效；多实例部署时各节点 presence 独立，光标/在线人数不跨节点一致。

### 7.8 DocCommentController 行为摘要

- 列表：只查 `parent_id IS NULL` 顶级评论，再逐条查 replies（N+1 查询）
- 创建：需 `X-User-Id`；支持 anchorIndex/anchorLength 锚定选区
- 更新：`resolved=1` 表示评论已解决（前端可隐藏）
- 删除：逻辑删 `deleted=1`

### 7.9 DocShareController 行为摘要

- 协作者 `targetType`：`USER` 或 `DEPT`；permission 枚举 VIEW/COMMENT/EDIT
- 分享 token：16 位 hex（UUID 去横线截取）；`expiredAt` 为空则永不过期
- `openByToken`：不校验登录；返回完整 content（只读分享场景）

---

## 8. 会议管理

### 8.1 实体 SysMeeting

主要字段：`title`, `room`, `creator_id`, `date`, `start_time`, `end_time`, `attendees`(TEXT), `status`, `join_url`, `meeting_id`(Zoom), `description`

### 8.2 API 详解

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/meetings` | 全量列表 |
| GET | `/api/meetings/my?userName=` | 创建者 **或** attendees 字符串包含 userName |
| POST | `/api/meetings` | 创建；room=`线上-Zoom` 且配置 OK 时调 Zoom |
| PUT | `/api/meetings/{id}` | 更新 |
| DELETE | `/api/meetings/{id}` | 删除 |
| POST | `/api/meetings/check-conflict` | 线下会议室冲突检测 |

### 8.3 `/my` 过滤逻辑

```java
filter(m -> m.getCreatorId().equals(userId)
    || (m.getAttendees() != null && m.getAttendees().contains(name)))
```

`name = userName` 参数非空用参数，否则用 `String.valueOf(userId)`。  
Workbench Feign 传 `userName=""` → 用 **userId 字符串** 匹配 attendees 文本。

### 8.4 冲突检测 check-conflict

- 线上-Zoom：**不检测**占用，直接返回无冲突
- 线下：同 `date` + 同 `room` + 时间段 overlap
- overlap：`startA < endB && endA > startB`（HH:mm 转分钟）
- 可选 `excludeId` 排除自身（编辑场景）

**Knowledge Agent**：`CheckMeetingConflictTool` → `CollaborationClient.checkMeetingConflict` → 此接口。

### 8.5 Zoom 集成（ZoomMeetingClient）

当 `room == "线上-Zoom"` 且 `zoomClient.isConfigured()`：

- 调用 Zoom API 创建会议
- 写入 `meeting_id`, `join_url`
- 失败则降级为本地会议 only

### 8.6 Workbench 缓存联动

| 操作 | evict userId |
|------|--------------|
| POST create | 当前创建者 |
| PUT update | 会议 creatorId |
| DELETE | 原 creatorId |

`DELETE http://8084/api/workbench/cache/user/{id}`

---

## 9. 待办 / 任务 / 审批 / 公告

### 9.1 待办 TodoController

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/todos` | `user_id = X-User-Id` |
| POST | `/api/todos` | done 默认 0 |
| PUT | `/{id}` | 改 title/priority/dueDate |
| PUT | `/{id}/toggle` | done 0↔1 |
| DELETE | `/{id}` | 物理删 |

字段：`done` 为 **Integer** 0/1（非 boolean）。

### 9.2 任务 TaskController

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks?status=` | **全库任务**，可选 status 过滤 |
| POST | `/api/tasks` | status 默认 `todo` |
| PUT | `/{id}` | 更新 |
| PUT | `/{id}/status` | 改状态 |
| DELETE | `/{id}` | |
| GET/POST | `/{taskId}/comments` | 评论 |

状态枚举：`todo` · `in_progress` · `review` · `done`

**注意**：list **不按** assignee/creator 过滤 — Workbench 统计为全局看板数据。

### 9.3 审批 ApprovalController

**列表**：

- `X-Is-Admin=true` → 全部申请
- 否则 → `user_id = 当前用户`

**创建** POST body：`type`, `title`, `formData`（JSON 字符串）

**审批** POST `/{id}/approve` body：`action`, `comment`

**多级状态机**（`nextStatus`）：

| type | 路径 |
|------|------|
| `leave` | pending → manager_approved → approved |
| 其他 | pending → manager_approved → finance_approved → approved |
| reject | action=rejected → status=rejected |

### 9.4 公告 AnnouncementController

GET 列表 · POST 发布 · DELETE 删除

### 9.5 通讯录 ContactController

| 方法 | 路径 | 数据源 |
|------|------|--------|
| GET | `/api/contacts/departments` | `sys_dept` |
| GET | `/api/contacts/users?deptId=` | `sys_user` 协同库 |

与 Gateway `/api/contacts`（gateway 库）是**两套数据**。

---

## 10. 意图树（IntentService + IntentController）

### 10.1 三层模型

| 实体 | 表 | 说明 |
|------|-----|------|
| KbIntentNode | `kb_intent_node` | parent_id 树；level 1=场景 2=意图 |
| KbIntentRule | `kb_intent_rule` | rule_type: keyword / regex |
| KbIntentKbRel | `kb_intent_kb_rel` | node → kb_id + weight |

### 10.2 REST 分组

**节点** `/api/intents/nodes`：GET 树 · GET/{id} · POST · PUT/{id} · DELETE/{id} · PUT/{id}/sort

**规则** `/api/intents/nodes/{id}/rules` · `/api/intents/rules/{ruleId}`

**知识库绑定** `/api/intents/nodes/{id}/kbs` · `/api/intents/kb-rel/{relId}`

**匹配** POST `/api/intents/match` body: `{ "query": "..." }`

### 10.3 match 算法

1. 加载 enabled 节点 + rules
2. 每条 rule：`keyword` → `query.contains(expression)`；`regex` → Pattern.find
3. 命中收集 nodeId/nodeName/weight
4. 按 weight 降序；返回 `hits` + `bestMatch`

**删除节点**：递归删子节点 + 规则 + KB 关联。

### 10.4 与 Workbench 的缺口

Workbench Feign 调用 `GET /api/intents?current=1&size=1` — **IntentController 无此根路径**，只有 `/nodes`。导致 `intentCount` 恒 0。

### 10.5 match 返回结构（IntentService）

```json
{
  "query": "用户原话",
  "hits": [
    { "nodeId": 1, "nodeName": "请假", "weight": 2.0, "ruleType": "keyword", "expression": "请假" }
  ],
  "bestMatch": { "nodeId": 1, "nodeName": "请假", "weight": 2.0 }
}
```

`match()` 全表加载 enabled 节点与规则；keyword 用 `contains`，regex 用 `Pattern.find`；hits 按 rule.weight 降序，首条为 bestMatch。

### 10.6 KbIntentNode 树构建

`getTree()` 一次 SELECT 全节点 → `groupingBy(parentId)` → 递归 `buildChildren` 写入实体 `children`（非 DB 列）。`getNode(id)` 额外 attach rules + kbRels 列表。

---

## 11. 关键词映射（KeywordMappingController）

表 `kb_keyword_mapping`：keyword、kb_name、priority、strategy、enabled 等（**无** intent_node_id / kb_id 外键，与意图树独立）。

| 方法 | 路径 |
|------|------|
| GET | `/api/keyword-mappings?current&size&keyword` | 分页 |
| POST/PUT/DELETE | CRUD |
| POST | `/match` body `{query}` | query.contains(keyword)，按 priority 降序 |

---

## 12. 数据库与迁移

**Schema**：`enterprise_collaboration`  
**Flyway**：禁用，脚本在 `src/main/resources/db/migration/`

| 脚本 | 内容 |
|------|------|
| `001-initial.sql` | 用户、部门、会议、待办、任务、审批、公告、文档、IM 全表 |
| `002-add-meeting-description.sql` | 会议 description |
| `003-doc-collaboration.sql` | 文档协作、评论、分享 |
| `004-intent-tree.sql` | 意图三表 |
| `005-intent-tree-seed.sql` | 意图种子 |
| `006-meeting-today-seed.sql` | 今日会议种子 |
| `V007__keyword_mapping.sql` | 关键词表 |
| `008-keyword-mapping-seed.sql` | 关键词种子 |
| `009-tasks-todos-approvals-seed.sql` | 办公流种子 |

**逻辑删除**：业务表普遍有 `deleted` 字段（MyBatis-Plus 全局配置）。

**Workbench 表**：同库 `wb_favorite`、`wb_user_layout`（由 workbench 服务写）。

### 12.1 ER 关系（简图）

```text
sys_dept ── sys_user
sys_user ── sys_todo / sys_meeting(creator) / sys_task / sys_approval_request
sys_doc ── sys_doc_operation / sys_doc_comment / sys_doc_collaborator / sys_doc_share_link
im_conversation ── im_conversation_member ── im_message ── im_message_read
kb_intent_node ── kb_intent_rule
kb_intent_node ── kb_intent_kb_rel
kb_keyword_mapping (独立)
```

### 12.2 001-initial.sql 建表清单（18 张）

| # | 表名 | 用途 |
|---|------|------|
| 1 | sys_user | 协同用户 |
| 2 | sys_dept | 部门 |
| 3 | sys_meeting | 会议 |
| 4 | sys_todo | 待办 |
| 5 | sys_task | 任务 |
| 6 | sys_task_comment | 任务评论 |
| 7 | sys_approval_request | 审批申请 |
| 8 | sys_approval_record | 审批记录 |
| 9 | sys_announcement | 公告 |
| 10 | sys_doc | 协同文档（初版无 version 列，003 追加） |
| 11 | im_conversation | IM 会话 |
| 12 | im_conversation_member | 会话成员 |
| 13 | im_message | 消息 |
| 14 | im_message_file | 文件附件元数据 |
| 15 | im_message_read | 已读游标 |
| 16 | wb_favorite | 工作台收藏（同库） |
| 17 | wb_user_layout | 工作台布局（同库） |

003 之后新增：sys_doc_operation、sys_doc_comment、sys_doc_share_link、sys_doc_collaborator；004 新增意图三表；V007 新增 kb_keyword_mapping。

### 12.3 MybatisPlusConfig

逻辑删除全局值：`deleted=1` 为删；部分 Controller 绕过 MP 直接 `deleteById` 物理删除。

---

## 13. 外部集成

### 13.1 Workbench 缓存失效

见 §8.6。配置：`app.workbench.service-url`

### 13.2 Knowledge Agent（CollaborationClient）

配置：`app.agent.collaboration.base-url`（默认 `http://localhost:8090`）

| Agent Tool | HTTP |
|------------|------|
| list_my_meetings | GET `/api/meetings/my?userName={userId}` |
| create_meeting | POST `/api/meetings` |
| check_meeting_conflict | POST `/api/meetings/check-conflict` |
| cancel_meeting | DELETE `/api/meetings/{id}` |

透传 Header：`X-User-Id`, `X-Is-Admin`, 可选 `X-Department-Id`, `X-Project-Id`

### 13.3 OSS（ImFileService）

`app.im.oss.endpoint/region/access-key/secret-key/bucket` — S3 兼容协议上传聊天文件。

### 13.4 前端直连

| 页面 | 连接 |
|------|------|
| Chats.vue | `ws://host:8090/ws/chat?token=` |
| Documents.vue | `ws://host:8090/ws/docs?token=` |
| Meetings.vue | REST 经 Gateway；错误提示 8090 |

Vite **未代理** WebSocket，必须端口 8090 可达。

---

## 14. 响应与错误

- 统一 `Result<T>`，`code="200"` 成功
- `BizException` → GlobalExceptionHandler
- JwtAuthFilter 401 直接写 JSON（非 Result 格式）：`{"code":"40100","message":"..."}`
- WS 认证失败：close 4001/4002 或 POLICY_VIOLATION

---

## 15. 阅读路线与问题反查

| 阶段 | 顺序 |
|------|------|
| ① | `FilterConfig` → `JwtAuthFilter` |
| ② | `ChatController` → `ImMessageService` → `ChatWebSocketHandler` |
| ③ | `DocWebSocketHandler` → `DocOTService` → `DocPermissionService` |
| ④ | `MeetingController` → `WorkbenchCacheNotifier` |
| ⑤ | `IntentService` → `KeywordMappingController` |
| ⑥ | `db/migration/001-initial.sql` |

| 现象 | 排查 |
|------|------|
| WS 连不上 | token 是否协同 JWT 或同 secret 的 Gateway JWT；8090 端口 |
| REST 401 | 经 Gateway 是否带 X-User-Id；直连是否 Bearer 协同 token |
| 文档 OT 冲突 | baseVersion 是否落后；多实例锁 |
| 工作台会议不刷新 | WorkbenchCacheNotifier、8084 可达 |
| intentCount=0 | 无 GET /api/intents 根路径 |
| IM 无推送 | onlineUsers 是否注册；RocketMQ 可选 |
| 审批状态卡住 | nextStatus 多级流转 |

---

## 16. 完整代码地图（逐包）

### 16.1 web/ Controller

| 文件 | 说明 |
|------|------|
| AuthController | 协同登录/register |
| MeetingController | 会议+冲突+Zoom+evict |
| TodoController | 待办 |
| TaskController | 任务+评论 |
| ApprovalController | 审批 |
| AnnouncementController | 公告 |
| ContactController | 通讯录 |
| ChatController | IM REST |
| DocController | 文档 CRUD |
| DocCommentController | 评论 |
| DocShareController | 协作者/分享 |
| IntentController | 意图树 |
| KeywordMappingController | 关键词 |

### 16.2 web/ WebSocket & Filter

| 文件 | 说明 |
|------|------|
| ChatWebSocketHandler | IM WS |
| DocWebSocketHandler | 文档 OT WS |
| JwtAuthFilter | /api/* 鉴权 |
| MutableRequestWrapper | 注入 Header |

### 16.3 service/

| 文件 | 说明 |
|------|------|
| DocOTService | OT+快照 |
| DocPermissionService | VIEW/COMMENT/EDIT |
| DocPresenceService | 文档房间在线 |
| IntentService | 意图树+match |
| ImMessageService | 发消息+MQ |
| ImMessageConsumer | MQ 消费+onlineUsers |
| ImReadService | 已读+未读数 |
| ImFileService | OSS 上传 |
| UserLoginServiceImpl | 登录+内存黑名单 |

### 16.4 integration/

| 文件 | 说明 |
|------|------|
| WorkbenchCacheNotifier | HTTP evict |
| ZoomMeetingClient | Zoom API |

---


## 17. 附录 A — REST 接口完整速查（逐路径）

> 以下路径均相对于 `http://localhost:8090`（经 Gateway 时为 `http://localhost:8086`）。  
> 成功响应统一为 `Result<T>`：`{ "code": "200", "message": "success", "data": ..., "traceId": "..." }`  
> 需身份接口默认依赖 Header：`X-User-Id`（Gateway 注入）；审批另需 `X-Is-Admin`。

---

### 17.1 AuthController — `/api/auth`

| 方法 | 路径 | 鉴权 | 请求体 / 参数 | 响应 data |
|------|------|------|---------------|-----------|
| POST | `/login` | 无 | `UserLoginReqDTO`: `{ username, password }` | `UserLoginRespDTO`: `{ accessToken, userId, username, realName, isAdmin }` |
| GET | `/check-login` | 无 | Query: `accessToken` | `boolean` |
| POST | `/logout` | 无 | Query: `accessToken` | void |
| POST | `/register` | 无 | `UserRegisterReqDTO`: `{ username, password, realName?, deptId? }` | `UserRegisterRespDTO` |
| POST | `/deletion` | 无 | `UserDeletionReqDTO`: `{ username, password }` | void |
| GET | `/has-username` | 无 | Query: `username` | `boolean` |

**说明**：`/login` 在 `JwtAuthFilter` 白名单；签发 token 使用 `auth.jwt.secret`，claims 含 `userId`（非 sub）。logout 将 token 写入 `UserLoginServiceImpl` 内存黑名单 Set。

---

### 17.2 MeetingController — `/api/meetings`

| 方法 | 路径 | Header | 请求体 | 响应 |
|------|------|--------|--------|------|
| GET | `/` | — | — | `List<SysMeeting>` 全量，按 date↓ startTime↑ |
| GET | `/my` | X-User-Id | Query: `userName`（默认空→用 userId 字符串匹配 attendees） | 过滤后的会议列表 |
| POST | `/` | X-User-Id | `MeetingReq` 见下 | `Long` 新 id |
| PUT | `/{id}` | — | `MeetingReq` | void |
| DELETE | `/{id}` | — | — | void；evict 原 creator 工作台缓存 |
| POST | `/check-conflict` | — | `ConflictCheckReq` 见下 | `ConflictCheckResp` |

**MeetingReq 字段**：

```json
{
  "title": "周会",
  "room": "301会议室",
  "date": "2026-05-25",
  "startTime": "10:00",
  "endTime": "11:00",
  "attendees": "张三,李四",
  "description": "可选说明"
}
```

- `room = "线上-Zoom"` 且 `zoom.account-id` 已配置 → 调 Zoom API，写入 `meetingId`、`joinUrl`
- 创建/更新后 `WorkbenchCacheNotifier.evictOverview(creatorId)`

**ConflictCheckReq**：

```json
{
  "room": "301会议室",
  "date": "2026-05-25",
  "startTime": "10:00",
  "endTime": "11:00",
  "excludeId": 123
}
```

**ConflictCheckResp**：`{ "conflict": true/false, "conflictingMeetings": [...] }`  
线上-Zoom 房间 **跳过** 冲突检测，恒返回无冲突。

---

### 17.3 TodoController — `/api/todos`

| 方法 | 路径 | Header | Body | 说明 |
|------|------|--------|------|------|
| GET | `/` | X-User-Id | — | `user_id = userId` 的待办 |
| POST | `/` | X-User-Id | `{ title, priority?, dueDate? }` | done 默认 0 |
| PUT | `/{id}` | — | 同上 | 更新 title/priority/dueDate |
| PUT | `/{id}/toggle` | — | — | done 0↔1 |
| DELETE | `/{id}` | — | — | **物理删除** |

响应字段 snake_case 混用：`due_date`, `created_at`（Task 同理）。

---

### 17.4 TaskController — `/api/tasks`

| 方法 | 路径 | Header | Body | 说明 |
|------|------|--------|------|------|
| GET | `/` | — | Query: `status?` | **全库**任务，可选 status 过滤 |
| POST | `/` | X-User-Id | `TaskReq`: title, description, assigneeId, priority | status=`todo` |
| PUT | `/{id}` | — | TaskReq | |
| PUT | `/{id}/status` | — | `{ "status": "in_progress" }` | |
| DELETE | `/{id}` | — | — | 物理删 |
| GET | `/{taskId}/comments` | — | — | `List<SysTaskComment>` |
| POST | `/{taskId}/comments` | X-User-Id | `{ "content": "..." }` | 写入 userName |

**status 枚举**：`todo` · `in_progress` · `review` · `done`

---

### 17.5 ApprovalController — `/api/approvals`

| 方法 | 路径 | Header | Body | 说明 |
|------|------|--------|------|------|
| GET | `/` | X-User-Id, X-Is-Admin | — | admin→全部；否则 userId 自己的 |
| POST | `/` | X-User-Id | `{ type, title, formData }` | formData 可为 JSON 字符串；status=pending |
| GET | `/{id}` | — | — | request + records 数组 |
| POST | `/{id}/approve` | X-User-Id | `{ action, comment }` | action: approve/reject 等 |

**状态机**（`nextStatus`，action≠rejected 时）：

| type | pending → | manager_approved → | finance_approved → |
|------|-----------|---------------------|---------------------|
| `leave` | manager_approved | approved | — |
| 其他 | manager_approved | finance_approved | approved |

reject：`action=rejected` → status=`rejected`，不再走 nextStatus。

---

### 17.6 AnnouncementController — `/api/announcements`

| 方法 | 路径 | Header | Body | 说明 |
|------|------|--------|------|------|
| GET | `/` | — | — | `@Cacheable("announcements")`；置顶优先 |
| POST | `/` | X-User-Id | `{ title, content }` | 写入 publisherId/Name |
| DELETE | `/{id}` | — | — | 物理删 |

---

### 17.7 ContactController — `/api/contacts`

| 方法 | 路径 | Query | 响应 | 缓存 |
|------|------|-------|------|------|
| GET | `/departments` | — | `List<SysDept>` | 无 |
| GET | `/users` | `deptId?` | id/username/realName/deptId/isAdmin | `@Cacheable("contacts_users")` key=deptId或'all' |

数据源：**协同库** `sys_user` / `sys_dept`，与 Gateway 通讯录无关。

---

### 17.8 ChatController — `/api/chat`

| 方法 | 路径 | Header | 参数/Body | 响应要点 |
|------|------|--------|-----------|----------|
| GET | `/conversations` | X-User-Id | — | id,name,type,last_msg*,unread,updatedAt |
| GET | `/unread-count` | X-User-Id | — | 整数总和 |
| GET | `/messages/{convId}` | — | page=1,size=50 | 按时间**升序**（先查降序再 reverse） |
| POST | `/conversations` | X-User-Id | CreateConvReq 见下 | 会话 id |
| POST | `/conversations/{id}/read` | X-User-Id | `{ lastReadMsgId }` | void |
| GET | `/members/{convId}` | — | — | 成员含 realName |
| POST | `/files/upload` | X-User-Id | multipart `file` | ossKey,fileName,fileSize,fileType |
| GET | `/files/{ossKey}` | — | Path 编码 key | ⚠ **未实现**（ImFileService.read 已有，缺 Controller） |

**CreateConvReq**：

```json
{
  "name": "群名",
  "type": "private",
  "memberIds": [2, 3]
}
```

- `type=private` 且仅 1 个 memberId：查是否已有双人 private 会话，有则返回已有 id
- 创建时插入 `im_conversation` + 各 `im_conversation_member`（含当前 userId）

---

### 17.9 DocController — `/api/docs`

| 方法 | 路径 | Header | 参数/Body | 说明 |
|------|------|--------|-----------|------|
| GET | `/` | — | keyword?, page=1, size=20 | 列表无 content |
| GET | `/{id}` | X-User-Id 可选 | — | content + version（DocOTService） |
| POST | `/` | X-User-Id | `{ title }` | 默认 Delta `{"ops":[{"insert":"\n"}]}` |
| PUT | `/{id}` | — | `{ title }` | 仅标题 |
| DELETE | `/{id}` | — | — | 物理删 |

---

### 17.10 DocCommentController — `/api/docs`

| 方法 | 路径 | Header | Body | 说明 |
|------|------|--------|------|------|
| GET | `/{docId}/comments` | — | — | 顶级评论 + replies 嵌套 + userName |
| POST | `/{docId}/comments` | X-User-Id | CommentReq | resolved=0 |
| PUT | `/comments/{id}` | — | content?, resolved? | 404 用 Results.failure |
| DELETE | `/comments/{id}` | — | — | 逻辑删 deleted=1 |

**CommentReq**：`content`, `anchorIndex?`, `anchorLength?`, `parentId?`（回复）

---

### 17.11 DocShareController — `/api`

| 方法 | 路径 | Body | 说明 |
|------|------|------|------|
| GET | `/docs/{docId}/collaborators` | — | targetType USER/DEPT + targetName(USER) |
| POST | `/docs/{docId}/collaborators` | targetType,targetId,permission | permission: VIEW/COMMENT/EDIT |
| PUT | `/collaborators/{id}` | permission | |
| DELETE | `/collaborators/{id}` | — | 逻辑删 |
| GET | `/docs/{docId}/shares` | — | token,permission,expiredAt |
| POST | `/docs/{docId}/shares` | permission,expiredAt? | token=16位 hex |
| DELETE | `/shares/{id}` | — | 逻辑删 |
| GET | `/docs/shared/{token}` | — | 公开读文档+permission（checkShareToken） |

---

### 17.12 IntentController — `/api/intents`

| 分组 | 方法 | 路径 | Body |
|------|------|------|------|
| 节点 | GET | `/nodes` | — 整树 |
| 节点 | GET | `/nodes/{id}` | — |
| 节点 | POST | `/nodes` | KbIntentNode 实体 |
| 节点 | PUT | `/nodes/{id}` | KbIntentNode |
| 节点 | DELETE | `/nodes/{id}` | 递归删子节点+规则+KB关联 |
| 节点 | PUT | `/nodes/{id}/sort` | `{ parentId, sortOrder }` |
| 规则 | GET | `/nodes/{id}/rules` | — |
| 规则 | POST | `/nodes/{id}/rules` | KbIntentRule |
| 规则 | PUT | `/rules/{ruleId}` | KbIntentRule |
| 规则 | DELETE | `/rules/{ruleId}` | — |
| KB | GET | `/nodes/{id}/kbs` | — |
| KB | POST | `/nodes/{id}/kbs` | `{ kbId, weight }` |
| KB | PUT | `/kb-rel/{relId}` | `{ weight }` |
| KB | DELETE | `/kb-rel/{relId}` | — |
| 匹配 | POST | `/match` | `{ "query": "..." }` → hits + bestMatch |

⚠ **无** `GET /api/intents` 根路径（Workbench Feign 误调导致 intentCount=0）。

---

### 17.13 KeywordMappingController — `/api/keyword-mappings`

| 方法 | 路径 | 参数/Body | 说明 |
|------|------|-----------|------|
| GET | `/` | current=1,size=20,keyword? | PageResult |
| POST | `/` | KbKeywordMapping | enabled 默认 1 |
| PUT | `/{id}` | KbKeywordMapping | |
| DELETE | `/{id}` | — | 物理删 |
| POST | `/match` | `{ "query" }` | enabled=1，query.contains(keyword)，priority↓ |

---

## 18. 附录 B — WebSocket 协议（完整字段）

### 18.1 Chat WS

**URL**：`ws://{host}:8090/ws/chat?token={JWT}`

**连接生命周期**：

```text
afterConnectionEstablished
  → JwtUtil.parse(token) + JwtClaimsSupport.resolveUserId
  → onlineUsers.put(userId, session)
  → 广播 { type:"status", userId, status:"online" } 给相关会话成员

afterConnectionClosed
  → onlineUsers.remove(userId)
  → 广播 offline
```

**客户端 → 服务端**（发消息，无 type 字段）：

```json
{
  "conversationId": 1,
  "content": "你好",
  "clientMsgId": "local-uuid-001"
}
```

**服务端 → 客户端**：

| type | 字段 | 说明 |
|------|------|------|
| ack | clientMsgId, serverMsgId, status | 发送确认 |
| message | id, conversationId, senderId, content, msgType, createdAt, ... | 新消息 |
| status | userId, status | online / offline |
| read | conversationId, userId, lastReadMsgId | 已读回执 |

**CloseStatus**：4001 过期 · 4002 无效 · POLICY_VIOLATION 无 token

---

### 18.2 Doc WS

**URL**：`ws://{host}:8090/ws/docs?token={JWT}`

所有消息含 `action` 字段。

**sub（订阅文档）** C→S：

```json
{ "action": "sub", "docId": 1001 }
```

S→C **init**：

```json
{
  "action": "init",
  "docId": 1001,
  "content": "{\"ops\":[...]}",
  "version": 42,
  "permission": "EDIT"
}
```

**op（提交 OT）** C→S：

```json
{
  "action": "op",
  "docId": 1001,
  "version": 42,
  "ops": [{ "retain": 5 }, { "insert": "新文字" }]
}
```

S→发送者 **ack**：`{ action:"ack", docId, version:43 }`  
S→其他订阅者 **op**：变换后的 ops + version

**cursor** / **presence**：`{ action, docId, range? / online? }` 双向广播

**error**：`{ action:"error", message:"..." }`（权限不足、版本错误等）

**断开清理**：`DocPresenceService.removeSession(sessionId)` → 对各 docId leave + presence offline

---

## 19. 附录 C — 关键调用链（扩展）

### C.1 IM 发消息（含 REST 已读）

```
POST /api/chat/conversations/{id}/read
  → ImReadService.markRead(userId, convId, lastReadMsgId)
  → UPSERT im_message_read
  → WS 推送 read 给会话内其他成员

WS 发消息
  → ImMessageService.send(senderId, convId, content, clientMsgId)
  → INSERT im_message (status=SENT)
  → UPDATE im_conversation last_msg_*
  → for member in conv: if onlineUsers.contains → session.send(message)
  → rocketMQTemplate.asyncSend("im-message", payload)
  → return ack

ImMessageConsumer.onMessage
  → 更新 message 状态 / conversation（幂等倾向）
  → 再次 push onlineUsers（可能与上一步重复）
```

### C.2 文档打开 → 协同编辑

```
GET /api/docs/{id}
  → DocOTService.getDocument → SysDoc.content + version

WS sub
  → DocPermissionService.checkPermission(docId, userId, null)
  → DocPresenceService.join
  → 返回 init

WS op
  → checkPermission ≥ EDIT
  → DocOTService.submitOperation(docId, userId, baseVersion, ops)
       lock → transform → INSERT sys_doc_operation → version++
       每 50 次 op → applyOpsToContent 写快照
  → ack + broadcast op
```

### C.3 分享链接只读

```
GET /api/docs/shared/{token}
  → DocPermissionService.checkShareToken(token)
  → DocOTService.getDocument(docId)
  → 返回 content + permission(VIEW/COMMENT/EDIT)
```

### C.4 意图 match（Knowledge RAG 路由前置）

```
POST /api/intents/match { query }
  → IntentService.match
  → 加载 enabled 节点 + rules
  → keyword: contains / regex: Pattern.find
  → 聚合 nodeId, weight → bestMatch

POST /api/keyword-mappings/match { query }
  → enabled 映射 query.contains(keyword) → hits by priority
```

### C.5 Agent 会议工具链

```
Knowledge AgentLoop → CheckMeetingConflictTool
  → CollaborationClient POST :8090/api/meetings/check-conflict
  → MeetingController.checkConflict
  → 同 room+date 时间段 overlap

CreateMeetingTool → POST /api/meetings + X-User-Id
ListMyMeetingsTool → GET /api/meetings/my?userName={userId}
```

---

## 20. 附录 D — Gotchas 与改进建议

| 项 | 现状 | 影响 | 建议 |
|----|------|------|------|
| 双 sys_user | Gateway ≠ Collaboration | 同 username 不同 id | SSO 或同步 |
| JwtAuthFilter Bearer | 只读 userId claim | Gateway JWT 无 userId 时 X-User-Id=null | Filter 改用 JwtClaimsSupport |
| WS token | 前端传 Gateway JWT | secret 不一致则 4002 | 统一 secret 或协同 login |
| 协同 logout 黑名单 | 内存 Set | 重启失效 | Redis/DB 黑名单 |
| IM 双推 | Service+Consumer 各推 | 客户端可能重复 message | clientMsgId 去重 |
| onlineUsers 静态 Map | 单 JVM | 多实例 IM 不同步 | Redis pub/sub |
| DocOT 内存锁 | ReentrantLock/docId | 多实例 OT 竞争 | 分布式锁 |
| Task 全库 | 无 user 过滤 | Workbench 统计非个人 | query assigneeId |
| GET /api/intents | 不存在 | Workbench intentCount=0 | 加 count 或改 Feign |
| 会议 /my | attendees 字符串 contains | 误匹配 | JSON 数组参会人 |
| Flyway off | 手工 SQL | 环境漂移 | 启用或 CI 校验 |
| Doc WS dept 权限 | checkPermission deptId=null | DEPT 协作者 WS 被拒 | 从 JWT/Header 传 deptId |
| Doc 创建者判定 | updatedBy 非 createdBy | 权限边界模糊 | 用 created_by |
| 公告/通讯录缓存 | @Cacheable 无 evict | 发布后列表 stale | @CacheEvict on POST |
| 物理删 | doc/task/todo/approval | 无审计 | 统一 deleted 标志 |
| IM 文件 GET | ChatController 无 download | Chats.vue 图片 404 | 实现 GET /files/{ossKey} |

---

## 21. 附录 E — 实体字段全表

### 21.1 用户与组织

**sys_user**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| username | VARCHAR | 登录名 |
| password | VARCHAR | BCrypt |
| real_name | VARCHAR | 显示名 |
| dept_id | BIGINT | 部门 |
| is_admin | TINYINT | 0/1 |
| enabled | TINYINT | 0/1 |

**sys_dept**：id, name, parent_id, sort_order, created_at

---

### 21.2 办公流

**sys_todo**：id, user_id, title, priority, due_date, done(0/1), created_at, updated_at

**sys_task**：id, title, description, creator_id, assignee_id, priority, status, due_date, created_at, updated_at

**sys_task_comment**：id, task_id, user_id, user_name, content, created_at

**sys_approval_request**：id, type, user_id, user_name, title, form_data(TEXT JSON), status, created_at, updated_at

**sys_approval_record**：id, request_id, approver_id, approver_name, action, comment, created_at

**sys_announcement**：id, title, content, publisher_id, publisher_name, is_pinned, created_at

**sys_meeting**：id, title, room, creator_id, date, start_time, end_time, attendees(TEXT), status, join_url, meeting_id(Zoom), description, created_at

---

### 21.3 协同文档

**sys_doc**：id, title, content(LONGTEXT Delta JSON), version, snapshot_version, updated_by, updated_by_name, created_by, updated_at, created_at

**sys_doc_operation**：id, doc_id, user_id, version, operation(LONGTEXT), created_at

**sys_doc_comment**：id, doc_id, user_id, content, anchor_index, anchor_length, parent_id, resolved, deleted, created_at, updated_at

**sys_doc_share_link**：id, doc_id, token(64), permission, expired_at, deleted, created_at

**sys_doc_collaborator**：id, doc_id, target_type(USER/DEPT), target_id, permission, deleted, created_at

---

### 21.4 IM

**im_conversation**：id, name, type(private/group), last_msg_content, last_msg_at, last_msg_sender, created_at, updated_at

**im_conversation_member**：id, conversation_id, user_id, role, joined_at

**im_message**：id, conversation_id, sender_id, content, msg_type(text/file), status, client_msg_id, mq_msg_id, created_at

**im_message_read**：id, user_id, conversation_id, last_read_msg_id, updated_at

**im_message_file**：id, message_id, oss_key, file_name, file_size, file_type

---

### 21.5 意图与关键词

**kb_intent_node**：id, parent_id, name, level(1场景/2意图), sort_order, enabled, description, created_at, updated_at

**kb_intent_rule**：id, node_id, rule_type(keyword/regex), expression, weight, enabled

**kb_intent_kb_rel**：id, node_id, kb_id, weight, created_at

**kb_keyword_mapping**：id, keyword, kb_name, priority, strategy, enabled, created_at, updated_at

---

## 22. 附录 F — Service 层方法索引

### DocOTService

| 方法 | 说明 |
|------|------|
| `submitOperation(docId, userId, baseVersion, ops)` | 加锁、transform、写 operation、版本+1、可选快照 |
| `getDocument(docId)` | DocSnapshot(content, version) |
| `getOpsSinceVersion(docId, sinceVersion)` | 增量 ops 列表 |
| `transform(opA, opB, isLeft)` | Quill Delta OT 核心 |

### DocPermissionService

| 方法 | 说明 |
|------|------|
| `checkPermission(docId, userId, deptId)` | EDIT/COMMENT/VIEW/null；创建者=updatedBy |
| `checkShareToken(token)` | 校验过期 + 返回 link.permission |

### DocPresenceService

| 方法 | 说明 |
|------|------|
| `join(docId, sessionId, userId, userName, session)` | 加入文档房间 |
| `leave(docId, sessionId)` | 离开 |
| `getSubscribers(docId)` | sessionId → SessionInfo |
| `getOnlineCount(docId)` | 在线数 |
| `trackSubscription(sessionId, docId)` | 记录 session 订阅集合 |
| `removeSession(sessionId)` | 断开时返回曾订阅的 docId 集 |

### IntentService

| 方法 | 说明 |
|------|------|
| `getTree()` / `getNode(id)` | 节点查询 |
| `createNode` / `updateNode` / `deleteNode` | CRUD；delete 递归 |
| `updateSort(id, parentId, sortOrder)` | 拖拽排序 |
| `getRules` / `createRule` / `updateRule` / `deleteRule` | 规则 CRUD |
| `getKbRels` / `bindKb` / `updateKbRel` / `unbindKb` | KB 关联 |
| `match(query)` | 规则命中 + bestMatch |

### ImMessageService

| 方法 | 说明 |
|------|------|
| `send(senderId, conversationId, content, clientMsgId)` | 入库、更新会话、在线推送、发 MQ |
| `sendFileMessage(...)` | 文件类型消息（若实现） |

### ImReadService

| 方法 | 说明 |
|------|------|
| `markRead(userId, convId, lastReadMsgId)` | upsert + WS read 通知 |
| `unreadCount(userId, convId)` | id > lastRead 计数 |

### ImFileService

| 方法 | 说明 |
|------|------|
| `isAvailable()` | access-key 是否配置 |
| `upload(MultipartFile)` | S3 put `im/{uuid}/{filename}` |
| `read(ossKey)` | S3 getObject 流 |

### UserLoginServiceImpl

| 方法 | 说明 |
|------|------|
| `login` / `register` / `deleteUser` | 协同账号 |
| `checkLogin` / `logout` | 校验 + 内存黑名单 |
| `hasUsername` | 重名检查 |

### ZoomMeetingClient

| 方法 | 说明 |
|------|------|
| `isConfigured()` | account-id 非空 |
| `createMeeting(topic, startTime, durationMinutes)` | OAuth + POST Zoom API |

### WorkbenchCacheNotifier

| 方法 | 说明 |
|------|------|
| `evictOverview(userId)` | HTTP DELETE workbench `/api/workbench/cache/user/{userId}` |

---

## 23. 附录 G — 前端页面 ↔ API 映射

| Vue 页面 | REST | WebSocket | 备注 |
|----------|------|-----------|------|
| Chats.vue | /api/chat/* | ws://:8090/ws/chat?token= | localStorage.token；文件 /api/chat/files/{ossKey} |
| Documents.vue | /api/docs, collaborators | ws://:8090/ws/docs?token= | OT 编辑、协作者面板 |
| Meetings.vue | /api/meetings | — | 经 Gateway |
| Todos.vue | /api/todos | — | toggle/delete |
| Tasks.vue | /api/tasks + comments | — | 看板四列 status |
| Approvals.vue | /api/approvals | — | approve/reject |
| Announcements.vue | /api/announcements | — | |
| admin/IntentConfig.vue | /api/intents/nodes/rules/kbs | — | 树形管理 |
| admin/IntentList.vue | /api/intents/nodes | — | 列表删节点 |
| Workbench（经 8084） | Feign → 本服务多接口 | — | unread-count, meetings/my, tasks, todos |

**Vite 代理**：REST `/api/*` → Gateway 8086；**WS 不代理**，浏览器直连 8090。

---

## 24. 附录 H — 数据库 DDL 摘录与执行顺序

**推荐手工执行顺序**（Flyway 关闭）：

```text
001-initial.sql
002-add-meeting-description.sql
003-doc-collaboration.sql
004-intent-tree.sql
005-intent-tree-seed.sql
006-meeting-today-seed.sql
V007__keyword_mapping.sql
008-keyword-mapping-seed.sql
009-tasks-todos-approvals-seed.sql
```

**003 核心 ALTER**（sys_doc 增 version/snapshot_version/created_by；新建 operation/comment/share/collaborator 四表）见 `003-doc-collaboration.sql`。

**MyBatis-Plus 全局**：逻辑删除字段 `deleted`（1=删）；部分 Controller 仍用 `deleteById` 物理删（task/todo/announcement/keyword）。

---

## 25. 附录 I — 配置项与环境变量清单

| 键 | 默认/示例 | 模块 |
|----|-----------|------|
| `server.port` | 8090 | |
| `spring.datasource.url` | jdbc:mysql://.../enterprise_collaboration | |
| `auth.jwt.secret` | **必配** | 协同 JWT |
| `auth.jwt.expiration` | 86400000 | |
| `rocketmq.name-server` | localhost:9876 | IM |
| `app.im.oss.endpoint` | MinIO/S3 地址 | IM 文件 |
| `app.im.oss.access-key` / `secret-key` / `bucket` / `region` | | 空则上传不可用 |
| `app.workbench.service-url` | http://localhost:8084 | 缓存失效 |
| `zoom.account-id` / `client-id` / `client-secret` | 空则 Zoom 降级 | 会议 |
| `spring.data.redis.host` | 可选 | @EnableCaching 公告/通讯录 |

---

## 26. 附录 J — 87 个 Java 源文件清单（按包）

```text
com.zjl.collaboration/
├── CollaborationApplication.java
├── config/ (5)
│   FilterConfig, WebSocketConfig, MybatisPlusConfig, ImOssProperties
├── web/ (17)
│   AuthController, MeetingController, TodoController, TaskController
│   ApprovalController, AnnouncementController, ContactController
│   ChatController, DocController, DocCommentController, DocShareController
│   IntentController, KeywordMappingController
│   ChatWebSocketHandler, DocWebSocketHandler, JwtAuthFilter, MutableRequestWrapper
├── service/ (11)
│   DocOTService, DocPermissionService, DocPresenceService, IntentService
│   ImMessageService, ImMessageConsumer, ImReadService, ImFileService
│   UserLoginService, impl/UserLoginServiceImpl
├── integration/ (2)
│   WorkbenchCacheNotifier, ZoomMeetingClient
├── dto/ (5)
│   UserLoginReqDTO/RespDTO, UserRegisterReqDTO/RespDTO, UserDeletionReqDTO
├── entity/ (25)
│   SysUser, SysDept, SysMeeting, SysTodo, SysTask, SysTaskComment
│   SysApprovalRequest, SysApprovalRecord, SysAnnouncement, SysDoc
│   SysDocOperation, SysDocComment, SysDocShareLink, SysDocCollaborator
│   ImConversation, ImConversationMember, ImMessage, ImMessageRead, ImMessageFile
│   KbIntentNode, KbIntentRule, KbIntentKbRel, KbKeywordMapping
├── mapper/ (25) — 与 entity 一一对应
└── util/ (2)
    JwtUtil, JwtClaimsSupport
```

---

## 27. 附录 K — 跨服务集成矩阵

| 调用方 | 被调接口 | 协议 | 身份传递 |
|--------|----------|------|----------|
| enterprise-web | 全部 /api/* | HTTP→Gateway→8090 | JWT→X-User-Id 等 |
| enterprise-web | /ws/chat, /ws/docs | WS 直连 8090 | ?token= |
| enterprise-workbench-service | meetings/my, todos, tasks, chat/unread-count, intents(误) | Feign HTTP | X-User-Id |
| enterprise-knowledge-ai-service | meetings CRUD, check-conflict | CollaborationClient HTTP | X-User-Id, X-Is-Admin |
| MeetingController | workbench cache evict | HTTP DELETE | 无 |
| ImMessageService | RocketMQ im-message | MQ | — |
| ImFileService | S3 兼容 OSS | AWS SDK | AK/SK |

---

## 28. 附录 L — 鉴权与权限决策（汇总图）

```text
                    HTTP /api/*
                         │
              ┌──────────▼──────────┐
              │   JwtAuthFilter     │
              └──────────┬──────────┘
         login白名单     │    X-User-Id 已有
              │         │         │
              ▼         ▼         ▼
           放行      Bearer解析   放行
                    (userId claim)
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
    Todo/Task      Approval(+Admin)   Doc/Chat
    user_id过滤     列表范围          X-User-Id
         │               │               │
         └───────────────┴───────────────┘
                         │
              DocPermissionService (文档)
              checkPermission / shareToken
```

```text
                    WebSocket
                         │
              token query param
                         │
              JwtUtil + JwtClaimsSupport
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
         ChatWebSocket          DocWebSocket
         onlineUsers Map        PresenceService
         ImMessageService       DocOTService + Permission
```

---

## 29. 附录 M — 种子数据概览（009 等）

| 脚本 | 内容摘要 |
|------|----------|
| 005-intent-tree-seed | 场景/意图节点 + 示例规则 |
| 006-meeting-today-seed | 当日会议样例 |
| 008-keyword-mapping-seed | 关键词→kb_name 映射 |
| 009-tasks-todos-approvals-seed | 待办、任务、审批样例数据 |

本地演示账号见各 seed SQL；协同 `sys_user` 与 Gateway 种子**可能 username 相同但 id 不同**。

---

## 30. 附录 N — 与 knowledge-service 文档的对照索引

| knowledge 文档章节 | collaboration 对应 |
|--------------------|----------------------|
| §15 REST 全量附录 | 本文 §17 |
| §16 调用链 | 本文 §19 |
| §17 Service 方法 | 本文 §22 |
| §14 代码地图 | 本文 §16 + §26 |
| 状态机 DocumentStatus | DocumentStatus 无；用 Task status + Approval status |
| Milvus/向量 | 无；意图 match 为规则引擎 |

---

*文档结束 · v3.0 · 2026-05-25*
