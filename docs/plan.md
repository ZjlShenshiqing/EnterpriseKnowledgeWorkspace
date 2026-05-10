# Plan 实施计划（完整修订版）

## 1. 当前进度总览

| 服务 | 状态 |
|------|------|
| enterprise-gateway-service | ✅ 完成（认证、JWT、RBAC、路由、限流、用户/部门/角色管理） |
| enterprise-knowledge-ai-service | ⚠️ Step3 完成 + 架构重构完成，Step4 待开发 |
| enterprise-collaboration-service | ⬜ 骨架（仅 Main.java） |
| enterprise-workbench-service | ⬜ 骨架（仅 Main.java） |

**新增模块**：

| 模块 | 位置 | 状态 |
|------|------|------|
| Agent 智能检索 + MCP Server | knowledge-ai-service 内嵌 | ⬜ 待开发（见阶段 2C） |
| 管理员后台 | 独立模块 | ⬜ 后续（见阶段七） |

## 2. 知识库微服务已完成的架构重构

以下 6 项重构已在 Step3 后完成（对应 git log）：

| # | 重构项 | Commit |
|---|--------|--------|
| R1 | 提取 VectorSyncService，合并重复的向量同步逻辑 | `00f1916` |
| R2 | 扁平化 Milvus 调用链 | `4a7b27f` |
| R3 | 拆分 KbDocumentServiceImpl 为独立职责服务 | `eb8e90f` |
| R4 | KbChunkServiceImpl 统一继承 ServiceImpl | `2da869e` |
| R5 | chunk_count 改为查询计数，避免并发偏差 | `03852b5` |
| R6 | 文件存储抽象层 + 文件下载接口 | `432a71a` |

### 2.1 剩余待修复项

1. **Chunk CRUD 的 DB 和 Milvus 不在同一事务**：chunk 插入在 Spring 事务内，Milvus gRPC 不在同一事务边界。
2. **异步分块失败后状态恢复不可靠**：线程池拒绝等场景会卡在 RUNNING。
3. **手动 Chunk 操作不记录 chunk_log**：只有异步分块任务写 `kb_document_chunk_log`。

---

## 3. 阶段 2B：数据一致性与容错（0.5 周）

### C1 — Chunk CRUD 事务边界修正

调整顺序：先写 Milvus（成功），再写 DB（事务内）。DB 失败时不回滚 Milvus（引入 outbox 补偿或接受最终一致性）。

### C2 — 异步分块失败重试与状态恢复

`DocumentChunkEventListener` 加入 3 次指数退避重试，最终失败时确保落 `FAILED` + chunk_log。

### C3 — 手动 Chunk 操作记录 chunk_log

`KbChunkServiceImpl` 的 create/update/delete/batchToggleEnabled 写入 `kb_document_chunk_log`。

---

## 4. 阶段 2C：Agent 智能检索 + MCP Server（新增，2 周）

> 详细设计见 `docs/superpowers/specs/2026-05-10-agent-mcp-design.md`

### 目标

在 knowledge-ai-service 中嵌入 LLM Agent + 标准 MCP Server，为员工提供自然语言知识检索能力。
**定位：纯检索助手**。只暴露查询接口，不暴露管理操作。

### 技术决策

| 维度 | 决策 |
|------|------|
| 意图引擎 | 完全 LLM Agent，LLM 输出 tool call |
| MCP 协议 | 标准 MCP Server，HTTP/SSE (Streamable HTTP) |
| 部署位置 | knowledge-ai-service 内嵌 |
| 工具粒度 | 细粒度，直接映射现有查询 Service |
| LLM 接入 | 接口抽象，默认 Anthropic SDK（claude-sonnet-4-6） |
| 会话管理 | DB 持久化（`kb_agent_session` + `kb_agent_message`） |
| 响应模式 | SSE 流式 |

### MCP Tool 清单

| Tool | 映射 | 关键参数 | 适用场景 |
|------|------|----------|----------|
| `search_documents` | `KbDocumentService.searchDocuments` | `keyword`, `limit` | "帮我找XX文档" |
| `list_documents` | `KbDocumentService.pageVisible` | `current`, `size` | "最近有哪些文档" |
| `get_document_detail` | `KbDocumentService.getVisible` | `documentId` | "这个文档的详细信息" |
| `list_knowledge_bases` | `KbKnowledgeBaseService.pageQuery` | — | "有哪些知识库" |

**返回字段限制**：只返回文档级别信息（标题、摘要、类型、状态、上传时间），不暴露 content_text、file_url、chunk 内部字段。

### 新增模块结构

```
agent/
├── AgentController.java          ← POST /api/kb/agent/chat + session CRUD
├── AgentLoop.java                ← Agent 循环
├── AgentSessionService.java      ← 会话/消息持久化
├── mcp/
│   ├── McpServer.java            ← MCP SSE 端点 (/mcp/sse, /mcp/tools, /mcp/messages)
│   ├── ToolRegistry.java         ← Tool 注册 + 分发
│   ├── ToolDefinition.java       ← Tool Schema
│   └── ToolResult.java           ← 执行结果
├── llm/
│   └── LlmClient.java            ← LLM 调用抽象
├── tool/
│   ├── SearchDocumentsTool.java
│   ├── ListDocumentsTool.java
│   ├── GetDocumentDetailTool.java
│   └── ListKnowledgeBasesTool.java
├── entity/
│   ├── KbAgentSession.java
│   └── KbAgentMessage.java
└── mapper/
    ├── KbAgentSessionMapper.java
    └── KbAgentMessageMapper.java
```

### 新增数据库表

```sql
CREATE TABLE kb_agent_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(256) NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id)
);

CREATE TABLE kb_agent_message (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NULL,
    tool_name VARCHAR(128) NULL,
    tool_input JSON NULL,
    tool_output JSON NULL,
    token_count INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_session (session_id, created_at)
);
```

### 新增 API

```
POST   /api/kb/agent/chat               ← 对话接口（SSE 流式）
GET    /api/kb/agent/sessions           ← 我的会话列表
GET    /api/kb/agent/sessions/{id}      ← 会话历史消息
DELETE /api/kb/agent/sessions/{id}      ← 归档会话

GET    /mcp/sse                         ← MCP 连接建立
POST   /mcp/tools/list                 ← 工具发现
POST   /mcp/messages?sessionId=xx      ← Tool 调用
```

### AgentLoop 流程

```
用户输入 → 加载会话 → 构建 messages(system+history+user) → 获取 tools JSON Schema
  → 调用 LLM (流式)
    → tool_call: 执行 tool（权限校验 + 参数校验）→ 回填结果 → 继续循环
    → text: 保存消息 → SSE 流式返回
```

### System Prompt

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

### 配置

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

### 安全约束

1. Tool 执行前走 `DocumentVisibilityService` 权限校验
2. Tool 参数合法性校验（limit ≤ 50 等）
3. ToolResult 不暴露 `content_text`、`file_url`、`chunk` 内部字段
4. 依赖现有 `UserContextInterceptor` 做身份解析

### 子任务

#### A1 — Agent 模块骨架 + 数据表
```
git add -A && git commit -m "feat(agent): Agent 模块骨架 + kb_agent_session/message 表"
```

#### A2 — LLM 客户端 + 配置
```
git add -A && git commit -m "feat(agent): LLM 客户端抽象 + Anthropic SDK 实现 + 配置"
```

#### A3 — ToolRegistry + 4 个 MCP Tool 实现
```
git add -A && git commit -m "feat(agent): ToolRegistry + 4 个检索 Tool 实现"
```

#### A4 — AgentLoop + SSE 流式响应
```
git add -A && git commit -m "feat(agent): AgentLoop 循环 + SSE 流式对话接口"
```

#### A5 — MCP Server 端点
```
git add -A && git commit -m "feat(agent): MCP Server HTTP/SSE 端点 + 工具发现"
```

#### A6 — 会话管理接口
```
git add -A && git commit -m "feat(agent): 会话列表/历史/归档接口"
```

#### A7 — 安全 + 权限校验
```
git add -A && git commit -m "feat(agent): Tool 执行权限校验 + 参数校验 + 字段过滤"
```

---

## 5. 阶段 2D：检索能力增强（1.5 周）

> Agent/MCP 提供了自然语言入口，但底层检索能力需要增强以支持更精准的搜索和后续 RAG 问答。

### S1 — Milvus 向量检索

`VectorSyncService` 增加 `search()` 方法，接入 Milvus `SearchReq`，支持 top-K 相似度检索。

### S2 — 接入真实 Embedding 模型

替换 `PlaceholderEmbeddingService`，支持配置驱动的模型切换（OpenAI text-embedding-ada-002 / 本地 bge-large-zh 等）。

### S3 — 全文检索接入

接入 Elasticsearch/OpenSearch：文档分块后同步写入 ES 索引，提供关键词+全文检索。

### S4 — 混合检索编排

向量检索 + 全文检索 → 结果合并/rerank，统一为 `SearchService`。

---

## 6. 阶段 2E：RAG 智能问答（1.5 周）

> 在 Agent + 检索能力基础上，实现基于文档内容的 RAG 问答。

### Q1 — QA 相关表 DDL

`qa_conversation`、`qa_message`、`qa_feedback` 表。与 Agent 的 `kb_agent_session`/`kb_agent_message` 区分——Agent 表用于工具调用对话，QA 表用于 RAG 问答。

### Q2 — RAG 问答核心流程

`POST /api/ai/qa/ask`：权限过滤 → 混合检索 → 组装 prompt → 调用 LLM → 返回答案+来源引用。

### Q3 — 多轮对话

conversationId 关联 + 上下文窗口管理。

### Q4 — 问答反馈

`POST /api/ai/qa/feedback` 记录用户评价。

### Q5 — 安全防护

无来源兜底、提示词注入防护、权限过滤。

---

## 7. 阶段三：协同业务服务 enterprise-collaboration-service（3 周）

### 3.1 — 工程骨架初始化

### 3.2 — 会议室管理

### 3.3 — 会议创建与冲突检测

### 3.4 — 会议修改、取消、详情、列表

### 3.5 — 会议参会人与通知

### 3.6 — 会议纪要

### 3.7 — 个人待办 CRUD

### 3.8 — 待办提醒

### 3.9 — 任务创建与分配

### 3.10 — 任务状态流转

### 3.11 — 任务评论

### 3.12 — 任务看板

### 3.13 — 消息通知中心

### 3.14 — 基础数据看板

（详细描述见原 plan.md 阶段三）

---

## 8. 阶段四：工作台聚合服务 enterprise-workbench-service（1 周）

### 4.1 — 工程骨架初始化

### 4.2 — 聚合接口：今日会议 + 今日待办

### 4.3 — 聚合接口：即将截止任务 + 未读通知 + 常用知识

### 4.4 — 个人工作统计

（详细描述见原 plan.md 阶段四）

---

## 9. 阶段五：联调与权限打通（1 周）

### 5.1 — 网关路由配置补全

### 5.2 — 用户身份透传验证

### 5.3 — 跨服务异常统一处理

### 5.4 — 操作日志补全

---

## 10. 阶段六：测试与上线准备（2 周）

### 6.1 — 单元测试

### 6.2 — 接口测试

### 6.3 — 权限专项测试

### 6.4 — AI 安全测试

### 6.5 — 部署脚本与监控

---

## 11. 阶段七：管理员后台模块（后续）

> 独立模块，不混入 MCP Agent 工具中。

管理员通过后台管理界面操作：
- 文档上传、分块、删除、启用/禁用
- 知识库创建/更新/删除
- Chunk 管理
- 分类管理
- 组织架构、角色权限、系统配置

---

## 12. 整体排期

| 阶段 | 周数 | 内容 |
|------|------|------|
| 阶段一 | ✅ | 网关、认证、用户、权限 |
| 阶段二-Step3 | ✅ | 知识库最小闭环 |
| 阶段 2A | ✅ | 知识库架构重构（R1-R6） |
| 阶段 2B | 0.5 周 | 数据一致性与容错（C1-C3） |
| 阶段 2C | 2 周 | Agent 智能检索 + MCP Server（A1-A7） |
| 阶段 2D | 1.5 周 | 检索能力增强（S1-S4） |
| 阶段 2E | 1.5 周 | RAG 智能问答（Q1-Q5） |
| 阶段三 | 3 周 | 协同业务（3.1-3.14） |
| 阶段四 | 1 周 | 工作台聚合（4.1-4.4） |
| 阶段五 | 1 周 | 联调与权限打通（5.1-5.4） |
| 阶段六 | 2 周 | 测试与上线准备（6.1-6.5） |
| 阶段七 | 后续 | 管理员后台模块 |
| **合计剩余** | **12.5 周** | |

---

## 13. 开发优先级

### P0（当前必须）

1. 阶段 2B：数据一致性修复
2. 阶段 2C：Agent 智能检索 + MCP Server
3. 阶段 2D：检索能力增强
4. 阶段三：协同业务（会议、待办、任务、通知）

### P1

1. 阶段 2E：RAG 智能问答
2. 阶段四：工作台聚合
3. 阶段五：联调

### P2

1. 阶段六：正式测试与上线
2. 阶段七：管理员后台模块
