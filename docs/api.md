# API 接口设计

## 1. 通用规则

### 1.1 基础路径

所有业务接口统一使用：

```text
/api
```

### 1.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "request-trace-id"
}
```

### 1.3 分页响应格式

```json
{
  "current": 1,
  "size": 20,
  "total": 100,
  "records": []
}
```

## 2. 认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/logout | 用户退出 |
| GET | /api/auth/profile | 获取当前用户信息 |
| GET | /api/auth/permissions | 获取当前用户权限 |

## 3. 知识库接口（`enterprise-knowledge-ai-service`，前缀 `/api/kb`）

> 实现细节、状态机与 Milvus Schema 见 **`docs/step3-summary.md`**。联调需请求头 `X-User-Id`（及权限相关头），见该文档第 7 节。

### 3.1 知识分类 `/api/kb/categories`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/kb/categories | 查询知识分类列表 |
| GET | /api/kb/categories/{id} | 分类详情 |
| POST | /api/kb/categories | 新增知识分类 |
| PUT | /api/kb/categories/{id} | 修改知识分类 |
| DELETE | /api/kb/categories/{id} | 删除知识分类（逻辑删除） |

### 3.2 逻辑知识库（多 Milvus 集合）`/api/kb/bases`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/kb/bases | 创建知识库（`name`、`collectionName`、可选 `embeddingModel`）；服务端创建并加载 Milvus 集合 |
| GET | /api/kb/bases | 分页查询（`current`、`size`、`name`） |
| GET | /api/kb/bases/{id} | 详情（含文档数量聚合） |
| PUT | /api/kb/bases/{id} | 更新（名称唯一、有向量化文档时不可改嵌入模型等） |
| PUT | /api/kb/bases/{id}/rename | 仅重命名 |
| DELETE | /api/kb/bases/{id} | 删除（无下属文档时允许） |

### 3.3 文档 `/api/kb/documents`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/kb/documents | 分页文档列表（权限过滤） |
| GET | /api/kb/documents/{id} | 文档详情 |
| POST | /api/kb/documents/upload | 上传：`multipart/form-data`，`meta`（JSON）+ `file`；上传成功为 **PENDING**；`meta` 可选 **`kbId`** 绑定知识库 |
| POST | /api/kb/documents/{id}/start-chunk | 提交异步分块（事务提交后监听执行） |
| POST | /api/kb/documents/{id}/execute-chunk | 立即执行分块（运维/补偿） |
| PUT | /api/kb/documents/{id} | 更新文档元数据（分块中等状态不可改，见实现） |
| PATCH | /api/kb/documents/{id}/enabled | Query：`on=true|false`，文档启用/禁用并同步向量 |
| GET | /api/kb/documents/{id}/chunk-logs | 分块任务日志分页 |
| GET | /api/kb/documents/search | 标题关键词搜索 |
| DELETE | /api/kb/documents/{id} | 删除文档（先删 Milvus 向量再删库） |

### 3.4 文档切片 `/api/kb/documents/{docId}/chunks`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/kb/documents/{docId}/chunks | 切片分页 |
| GET | /api/kb/documents/{docId}/chunks/list | 切片列表 |
| POST | /api/kb/documents/{docId}/chunks | 新增切片（写向量） |
| POST | /api/kb/documents/{docId}/chunks/batch | Query：`writeVector`；批量新增 |
| PUT | /api/kb/documents/{docId}/chunks/{chunkId} | 更新切片（Upsert 向量） |
| DELETE | /api/kb/documents/{docId}/chunks/{chunkId} | 删除切片 |
| PATCH | /api/kb/documents/{docId}/chunks/{chunkId}/enabled | Query：`on`；启用/禁用切片 |
| POST | /api/kb/documents/{docId}/chunks/batch-enabled | Query：`on`；批量启用/禁用 |

## 4. Agent 智能检索接口（`enterprise-knowledge-ai-service`）

> 详细设计见 `docs/superpowers/specs/2026-05-10-agent-mcp-design.md`

### 4.1 对话接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/kb/agent/chat | 发起对话（SSE 流式响应） |

**请求**：

```json
{
  "sessionId": null,
  "message": "帮我找关于微服务架构的文档"
}
```

**响应（SSE 流）**：

```
event: message     → {"delta": "找到 3 篇相关文档...", "type": "text"}
event: tool_call   → {"tool": "search_documents", "args": {"keyword": "微服务架构"}}
event: tool_result → {"tool": "search_documents", "result": [...]}
event: error       → {"message": "错误信息"}
event: done        → {"sessionId": 123, "tokenUsage": {"input": 500, "output": 200}}
```

### 4.2 会话管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/kb/agent/sessions | 我的会话列表 |
| GET | /api/kb/agent/sessions/{id} | 会话历史消息 |
| DELETE | /api/kb/agent/sessions/{id} | 归档会话 |

### 4.3 MCP Server 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /mcp/sse | 建立 MCP SSE 连接，返回 sessionId |
| POST | /mcp/tools/list | 返回所有注册的 Tool 定义（JSON Schema） |
| POST | /mcp/messages?sessionId=xx | 接收 tool call 请求，返回 tool result |

**MCP Tool 清单**：

| Tool | 映射 | 关键参数 | 适用场景 |
|------|------|----------|----------|
| `search_documents` | `searchDocuments` | `keyword`, `limit` | 搜索文档 |
| `list_documents` | `pageVisible` | `current`, `size` | 文档列表 |
| `get_document_detail` | `getVisible` | `documentId` | 文档详情 |
| `list_knowledge_bases` | `pageQuery` | — | 知识库列表 |

## 5. 智能问答接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/ai/qa/ask | 发起智能问答 |
| GET | /api/ai/qa/history | 查询问答历史 |
| POST | /api/ai/qa/feedback | 提交回答反馈 |

### 问答请求示例

```json
{
  "question": "差旅报销需要哪些材料？",
  "conversationId": "optional"
}
```

### 问答响应示例

```json
{
  "answer": "根据公司差旅报销制度，员工需要提交审批单、发票、行程单等材料。",
  "sources": [
    {
      "documentId": 1001,
      "documentTitle": "公司差旅报销制度",
      "chunkText": "差旅报销需提交审批单、发票和行程证明。"
    }
  ]
}
```

## 6. 会议接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/meeting/rooms | 查询会议室 |
| POST | /api/meeting/rooms | 新增会议室 |
| PUT | /api/meeting/rooms/{id} | 修改会议室 |
| DELETE | /api/meeting/rooms/{id} | 删除会议室 |
| GET | /api/meetings | 查询会议列表 |
| POST | /api/meetings | 创建会议 |
| GET | /api/meetings/{id} | 查看会议详情 |
| PUT | /api/meetings/{id} | 修改会议 |
| POST | /api/meetings/{id}/cancel | 取消会议 |
| POST | /api/meetings/check-conflict | 检查会议冲突 |

## 7. 待办接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/todos | 查询我的待办 |
| POST | /api/todos | 新建待办 |
| GET | /api/todos/{id} | 查看待办详情 |
| PUT | /api/todos/{id} | 修改待办 |
| POST | /api/todos/{id}/complete | 完成待办 |
| DELETE | /api/todos/{id} | 删除待办 |

## 8. 任务接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/tasks | 查询任务列表 |
| POST | /api/tasks | 创建任务 |
| GET | /api/tasks/{id} | 查看任务详情 |
| PUT | /api/tasks/{id} | 修改任务 |
| POST | /api/tasks/{id}/status | 更新任务状态 |
| POST | /api/tasks/{id}/comments | 新增任务评论 |
| GET | /api/tasks/board | 查看任务看板 |

## 9. 消息通知接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/notifications | 查询通知列表 |
| GET | /api/notifications/unread-count | 获取未读数量 |
| POST | /api/notifications/{id}/read | 标记单条已读 |
| POST | /api/notifications/read-all | 全部标记已读 |

## 10. 数据看板接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/dashboard/knowledge | 获取知识库指标 |
| GET | /api/dashboard/meeting | 获取会议指标 |
| GET | /api/dashboard/task | 获取任务指标 |
| GET | /api/dashboard/personal | 获取个人指标 |

## 11. 系统管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/system/users | 查询用户 |
| POST | /api/system/users | 新增用户 |
| PUT | /api/system/users/{id} | 修改用户 |
| DELETE | /api/system/users/{id} | 删除用户 |
| GET | /api/system/departments | 查询部门 |
| POST | /api/system/departments | 新增部门 |
| GET | /api/system/roles | 查询角色 |
| POST | /api/system/roles | 新增角色 |
| GET | /api/system/logs | 查询操作日志 |
