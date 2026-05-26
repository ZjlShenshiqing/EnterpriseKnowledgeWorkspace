# IM 聊天系统改造 — 可靠消息 + 已读追踪 + 文件发送

## 目标

解决当前 IM 的三个核心问题，构建完整可用的即时通讯：

1. **消息可靠性**：引入 RocketMQ，确保消息不丢失、离线可恢复
2. **消息级已读追踪**：每条消息可追踪已读/未读状态
3. **文件发送**：支持图片和文件上传，存入阿里云 OSS

## 架构概览

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐
│  Vue 前端 │────▶│  HTTP REST   │     │  RocketMQ    │
│  (Chats)  │     │  /api/chat/* │     │  Topic:      │
│  WebSocket│     └──────┬───────┘     │  im-message   │
└────┬─────┘            │             └──────┬───────┘
     │                  │                    │
     │            ┌─────▼─────────┐   ┌──────▼────────┐
     └───WS──────▶│ ImMessageService│──▶│ Consumer      │
                  │ (生产者)        │   │ (持久化+推送)  │
                  └────────────────┘   └──────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  WebSocket      │
                                    │  在线用户推送    │
                                    │  + 浏览器通知    │
                                    └─────────────────┘
```

### 模块关系

- **collaboration-service**（8082）：承载所有 IM 后端逻辑、WebSocket、RocketMQ 生产者/消费者
- **RocketMQ**：消息中间件，确保可靠投递和重试
- **前端 Chats.vue + Contacts.vue**：飞书风格 UI（已在上一轮设计中完成）

## 数据模型

### 现有表修改

**`im_message` 加字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | VARCHAR(16) | `SENDING` → `SENT` → `DELIVERED` → `READ` / `FAILED` |
| `mq_msg_id` | VARCHAR(64) | RocketMQ 消息 ID，用于幂等去重和重试追踪 |

**`im_conversation` 加字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `last_msg_content` | VARCHAR(512) | 最后一条消息摘要 |
| `last_msg_at` | TIMESTAMP | 最后消息时间 |
| `last_msg_sender` | VARCHAR(64) | 最后消息发送者名称 |

### 新建表

**`im_message_read` — 消息级已读追踪：**

```sql
CREATE TABLE im_message_read (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    last_read_msg_id BIGINT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

未读数实时计算：`COUNT(*) FROM im_message WHERE conversation_id=? AND id > last_read_msg_id`，不单独存储，避免不一致。

**`im_message_file` — 文件附件：**

```sql
CREATE TABLE im_message_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    oss_key VARCHAR(512) NOT NULL,
    thumb_oss_key VARCHAR(512) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## API 设计

### HTTP Endpoints

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/conversations` | 会话列表（含未读数、最后消息） |
| POST | `/api/chat/conversations` | 创建会话（私聊去重：已有则返回旧会话） |
| GET | `/api/chat/messages/{convId}` | 历史消息（分页，`page`/`size` 参数） |
| GET | `/api/chat/members/{convId}` | 会话成员列表 |
| POST | `/api/chat/conversations/{id}/read` | 标记已读 `{ lastReadMsgId }` |
| POST | `/api/chat/files/upload` | 上传文件到 OSS，返回文件元信息 |
| GET | `/api/chat/files/{ossKey}` | 获取文件（代理 OSS 或返回重定向 URL） |

### WebSocket 消息类型

| type | 方向 | 负载 | 说明 |
|------|------|------|------|
| `message` | 客户端→服务端 | `{ conversationId, content, clientMsgId, files? }` | 发送消息 |
| `ack` | 服务端→客户端 | `{ clientMsgId, serverMsgId, status }` | 消息发送确认 |
| `message` | 服务端→客户端 | `{ id, conversationId, senderId, senderName, content, status, createdAt, files? }` | 收到新消息 |
| `read` | 服务端→客户端 | `{ conversationId, userId, lastReadMsgId }` | 对方已读通知 |
| `status` | 服务端→客户端 | `{ userId, status }` | 用户上线/离线（已有） |

## 核心流程

### 发送消息

```
1. 用户输入 → 前端生成 clientMsgId → 本地乐观添加临时消息
2. ws.send({ type: "message", conversationId, content, clientMsgId })
3. WebSocket Handler 接收 → 委托 ImMessageService.send()
4. ImMessageService:
   a. 创建 ImMessage 实体 (status=SENDING)，生成 serverMsgId
   b. 构造 RocketMQ 消息，key=serverMsgId
   c. 同步发送到 Topic: im-message（同步确保消息一定投递成功）
   d. 返回 ACK: { clientMsgId, serverMsgId, status: "SENDING" }
5. RocketMQ Consumer:
   a. 幂等检查（mq_msg_id）
   b. 持久化到 MySQL (status → SENT)
   c. 查询会话所有成员
   d. 对每个在线成员 → ws.send 推送消息
   e. 更新 im_conversation.last_msg_*
   f. 如有失败，MQ 自动重试
```

### 发送失败处理

- **MQ 投递失败**：前端收到 `ack` 带 `status: "FAILED"`，本地消息标红显示"发送失败，点击重试"
- **Consumer 处理失败**：RocketMQ 自动重试（最多 3 次），超过后进入死信队列
- **客户端离线**：消息已持久化，用户下次打开会话时通过 GET API 拉取

### 标记已读

```
1. 用户打开会话或滚动到底部
2. POST /api/chat/conversations/{id}/read { lastReadMsgId }
3. 更新 im_message_read 表
4. 批量更新 im_message 中 sender 的消息状态 → READ
5. 通过 WS 通知发送者: { type: "read", conversationId, userId, lastReadMsgId }
```

### 文件上传

```
1. 用户在聊天框选择文件
2. POST /api/chat/files/upload (multipart/form-data)
   → 后端接收 MultipartFile → 上传到 OSS
   → 返回 { ossKey, fileName, fileSize, fileType, thumbOssKey? }
3. 前端拿到 OSS 信息后附在消息中发送
4. 渲染时：图片直接展示（带预览），文件显示卡片（图标+名称+大小+下载）
```

图片缩略图：宽度 > 500px 时用 Java 生成缩略图同路径上传 OSS，前端列表/气泡中加载缩略图。

## 需要新增的后端组件

### Service 层

| 类 | 职责 |
|------|------|
| `ImMessageService` | 发送消息（生产者）、查询消息、文件上传 |
| `ImReadService` | 已读标记 → 写 im_message_read，通知发送者 |
| `ImMessageConsumer` | RocketMQ 消费者：持久化、推送在线用户、更新会话信息 |
| `ImFileService` | OSS 文件上传/下载（复用 S3FileStorageService 模式） |

### 配置

- RocketMQ NameServer 地址（已有 compose 文件在 `resouces/docker/`）
- OSS 配置（复用知识库的 endpoint/access-key/secret-key，新增 IM 文件专用 bucket）

### 数据库迁移

- `im_message` 表新增 `status`、`mq_msg_id` 列
- `im_conversation` 表新增 `last_msg_*` 列
- 新建 `im_message_read`、`im_message_file` 表

## 前端改动

在上一轮 UI 改造基础上增加：

- 消息发送 ACK 处理（用 serverMsgId 替换临时消息）
- 发送失败消息的状态显示与重试按钮
- 已读/未读状态展示
- 图片/文件消息的气泡渲染
- 文件上传（粘贴/拖拽/按钮三种方式）
- 浏览器通知 API（`Notification.requestPermission()`）
- 会话列表未读角标（从 API 获取）

## 错误处理

- API 失败：`ElMessage.error(message)` 提示
- WebSocket 断连：静默重连（3 秒间隔），断连期间的消息通过 HTTP API 拉取补齐
- 消息发送失败：消息气泡标红 + 感叹号图标 + 点击重试
- 文件上传失败：`ElMessage.error` + 消息输入框不清空

## 测试要点

- 两个浏览器窗口各登录不同用户，互发消息，验证实时性和已读标记
- 关闭一方窗口，另一方发消息 → 重新打开窗口 → 消息不丢失，未读计数正确
- WebSocket 断连后重连，消息补齐
- 图片/文件上传、预览、下载
- 私聊去重：不会创建重复会话
- 消息发送失败 → 重试成功
- 浏览器通知弹出
