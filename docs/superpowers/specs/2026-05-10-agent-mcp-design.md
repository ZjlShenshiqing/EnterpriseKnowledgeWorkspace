# Agent 智能检索 + MCP Server 设计文档

> 2026-05-10 | enterprise-knowledge-ai-service

## 目标

在 knowledge-ai-service 中嵌入 LLM Agent + MCP Server，为员工提供自然语言知识检索能力。这是企业智能工作平台的第一步。

## 需求摘要

| 能力域 | 目标用户 | 本阶段范围 |
|--------|----------|-----------|
| 知识检索 | 全体员工 | 自然语言搜索文档，MCP 提供工具发现 |
| 智能问答（RAG） | 全体员工 | 后续 |
| 会议室预约 | 全体员工 | 后续（collaboration-service） |
| 个人待办/工作安排 | 全体员工 | 后续（workbench-service） |
| 任务分配/进度跟踪 | 管理者 | 后续（workbench-service） |
| 组织架构/权限/系统配置 | 管理员 | 后续（独立 admin 模块） |

**定位**：纯检索助手。只暴露查询接口，不暴露管理操作（上传/分块/删除等）。

## 技术决策

| 维度 | 决策 |
|------|------|
| 意图引擎 | 完全 LLM Agent，LLM 输出 tool call |
| MCP 协议 | 标准 MCP Server，HTTP/SSE (Streamable HTTP) 传输 |
| 部署位置 | knowledge-ai-service 内嵌 |
| 工具粒度 | 细粒度，直接映射现有查询服务 |
| LLM 接入 | 接口抽象，默认 Anthropic SDK |
| 会话管理 | DB 持久化 |
| 响应模式 | SSE 流式 |

## 系统架构

```
enterprise-knowledge-ai-service
├── agent/                          ← 新增模块
│   ├── AgentController.java        ← 对话 + 会话管理 API
│   ├── AgentLoop.java              ← Agent 循环
│   ├── AgentSessionService.java    ← 会话持久化
│   ├── mcp/
│   │   ├── McpServer.java          ← MCP SSE 端点
│   │   ├── ToolRegistry.java       ← Tool 注册 + 分发
│   │   ├── ToolDefinition.java     ← Tool Schema
│   │   └── ToolResult.java         ← 执行结果
│   ├── llm/
│   │   └── LlmClient.java          ← LLM 调用抽象
│   ├── tool/
│   │   ├── SearchDocumentsTool.java
│   │   ├── ListDocumentsTool.java
│   │   ├── GetDocumentDetailTool.java
│   │   └── ListKnowledgeBasesTool.java
│   ├── entity/
│   │   ├── KbAgentSession.java
│   │   └── KbAgentMessage.java
│   └── mapper/
│       ├── KbAgentSessionMapper.java
│       └── KbAgentMessageMapper.java
│
├── web/
│   └── AgentController.java        ← 新增
│
└── (现有模块不变)
```

## MCP Tool 设计（4 个）

| Tool | 映射 | 参数 | 场景 |
|------|------|------|------|
| `search_documents` | `KbDocumentService.searchDocuments` | `keyword`(必填), `limit`(默认10) | "帮我找XX文档" |
| `list_documents` | `KbDocumentService.pageVisible` | `current`(默认1), `size`(默认20) | "最近有哪些文档" |
| `get_document_detail` | `KbDocumentService.getVisible` | `documentId`(必填) | "这个文档的详细信息" |
| `list_knowledge_bases` | `KbKnowledgeBaseService.pageQuery` | — | "有哪些知识库" |

**返回字段限制**：只返回文档级别信息（标题、摘要、类型、状态、上传时间），不暴露 content_text、file_url、chunk 详情。

## AgentLoop 流程

```
用户输入 "帮我找关于微服务架构的文档"
  │
  ├── ① 加载/创建会话
  ├── ② 构建 messages（system prompt + history + user message）
  ├── ③ 获取 tools JSON Schema
  ├── ④ 调用 LLM (流式)
  ├── ⑤ 判断响应
  │     ├── tool_call → 执行 tool → 回填结果 → 回到 ④
  │     └── text → 保存消息 → 流式返回
  └── ⑥ 安全约束
        ├── Tool 执行前做权限校验（复用 DocumentVisibilityService）
        ├── 参数合法性校验
        └── 只返回当前用户可见的文档
```

**System Prompt**：

```
你是企业知识库助手。你的职责是帮助员工查找和了解公司内部文档。

可用工具：search_documents、list_documents、get_document_detail、list_knowledge_bases

规则：
1. 只回答与知识库文档相关的问题
2. 当用户询问文档内容时，先搜索再回答
3. 回答时引用具体的文档标题和来源
4. 不要编造信息，找不到就说找不到
5. 不讨论文档上传、删除、分块等管理操作
```

## 数据模型

```sql
CREATE TABLE kb_agent_session (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(256) NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id)
);

CREATE TABLE kb_agent_message (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,           -- user / assistant / tool
    content LONGTEXT NULL,
    tool_name VARCHAR(128) NULL,
    tool_input JSON NULL,
    tool_output JSON NULL,
    token_count INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_session (session_id, created_at)
);
```

## API 契约

### POST /api/kb/agent/chat（对话）

```
Request:  { "sessionId": null|123, "message": "帮我找XX文档" }
Response: SSE 流
  event: message     → {"delta": "文本", "type": "text"}
  event: tool_call   → {"tool": "search_documents", "args": {...}}
  event: tool_result → {"tool": "search_documents", "result": [...]}
  event: error       → {"message": "错误信息"}
  event: done        → {"sessionId": 123, "tokenUsage": {...}}
```

### 会话管理

```
GET    /api/kb/agent/sessions          → 我的会话列表
GET    /api/kb/agent/sessions/{id}     → 会话历史消息
DELETE /api/kb/agent/sessions/{id}     → 归档会话
```

### MCP 端点

```
GET  /mcp/sse                    → 建立 MCP 连接
POST /mcp/tools/list            → 工具发现
POST /mcp/messages?sessionId=xx → Tool 调用
```

## LLM 客户端抽象

```java
public interface LlmClient {
    void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener);
}

public interface StreamListener {
    void onTextDelta(String delta);
    void onToolCall(ToolCall call);
    void onDone(ChatUsage usage);
    void onError(Throwable error);
}
```

默认实现：Anthropic Messages API (claude-sonnet-4-6)，低 temperature (0.3)。

## 配置

```yaml
app:
  agent:
    llm:
      provider: anthropic
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-sonnet-4-6
      max-tokens: 4096
      temperature: 0.3
    session:
      max-history: 50
      archive-after-days: 30
```

## 错误处理

| 异常 | 处理 |
|------|------|
| LLM 调了不存在的 tool | 返回 ToolNotFoundException 给 LLM，让它重试 |
| Tool 执行失败 | 返回 ToolExecutionException 给 LLM |
| 超过最大轮次 (MAX_TURNS=10) | 强制结束，提示用户 |
| 权限不足 | 不暴露存在性，统一返回"未找到" |

## 安全约束

1. Tool 执行前走 `DocumentVisibilityService` 权限校验
2. Tool 参数做合法性校验（limit ≤ 50 等）
3. ToolResult 不暴露 `content_text`、`file_url`、`chunk` 内部字段
4. 依赖现有的 `UserContextInterceptor` 做身份解析

## 不在本阶段范围

- RAG 问答（基于文档内容的向量检索 + 生成）
- 会议室预约、待办管理、任务管理
- 管理员后台模块
- Agent 自主决策（多步推理链路）
