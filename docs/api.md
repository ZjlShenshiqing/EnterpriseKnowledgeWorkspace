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

## 3. 知识库接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/kb/documents | 查询文档列表 |
| POST | /api/kb/documents/upload | 上传文档 |
| GET | /api/kb/documents/{id} | 查看文档详情 |
| PUT | /api/kb/documents/{id} | 修改文档信息 |
| DELETE | /api/kb/documents/{id} | 删除文档 |
| GET | /api/kb/categories | 查询知识分类 |
| POST | /api/kb/categories | 新增知识分类 |
| PUT | /api/kb/categories/{id} | 修改知识分类 |
| DELETE | /api/kb/categories/{id} | 删除知识分类 |

## 4. 智能问答接口

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

## 5. 会议接口

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

## 6. 待办接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/todos | 查询我的待办 |
| POST | /api/todos | 新建待办 |
| GET | /api/todos/{id} | 查看待办详情 |
| PUT | /api/todos/{id} | 修改待办 |
| POST | /api/todos/{id}/complete | 完成待办 |
| DELETE | /api/todos/{id} | 删除待办 |

## 7. 任务接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/tasks | 查询任务列表 |
| POST | /api/tasks | 创建任务 |
| GET | /api/tasks/{id} | 查看任务详情 |
| PUT | /api/tasks/{id} | 修改任务 |
| POST | /api/tasks/{id}/status | 更新任务状态 |
| POST | /api/tasks/{id}/comments | 新增任务评论 |
| GET | /api/tasks/board | 查看任务看板 |

## 8. 消息通知接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/notifications | 查询通知列表 |
| GET | /api/notifications/unread-count | 获取未读数量 |
| POST | /api/notifications/{id}/read | 标记单条已读 |
| POST | /api/notifications/read-all | 全部标记已读 |

## 9. 数据看板接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/dashboard/knowledge | 获取知识库指标 |
| GET | /api/dashboard/meeting | 获取会议指标 |
| GET | /api/dashboard/task | 获取任务指标 |
| GET | /api/dashboard/personal | 获取个人指标 |

## 10. 系统管理接口

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
