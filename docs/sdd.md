# SDD 软件设计文档

## 1. 系统总体架构

```mermaid
flowchart TB
    User[企业内部用户] --> Web[PC Web 前端]
    Admin[系统管理员] --> Web

    Web --> Gateway[统一入口 / 后端 API]

    Gateway --> Auth[认证与权限模块]
    Gateway --> Workbench[工作台模块]
    Gateway --> Knowledge[知识库模块]
    Gateway --> Agent[Agent 智能检索模块]
    Gateway --> AIQA[智能问答模块]
    Gateway --> Calendar[日历与时间管理模块]
    Gateway --> Meeting[会议预约模块]
    Gateway --> Todo[待办事项模块]
    Gateway --> Task[任务协同模块]
    Gateway --> Notify[消息通知模块]
    Gateway --> Dashboard[数据看板模块]
    Gateway --> System[系统管理模块]
    Gateway --> Admin[管理员后台模块]

    Knowledge --> FileStorage[文件存储]
    Knowledge --> Search[全文检索]
    Knowledge --> VectorDB[向量数据库]
    Agent --> LLM[大模型服务]
    Agent --> Knowledge
    Agent --> MCP[MCP Server<br/>工具发现与分发]
    AIQA --> Search
    AIQA --> VectorDB
    AIQA --> LLM

    Meeting --> Notify
    Todo --> Notify
    Task --> Notify

    Auth --> DB[(关系型数据库)]
    Knowledge --> DB
    Meeting --> DB
    Todo --> DB
    Task --> DB
    Notify --> DB
    Dashboard --> DB
```

## 2. 分层架构

```mermaid
flowchart LR
    Presentation[表现层] --> Controller[接口控制层]
    Controller --> Service[业务服务层]
    Service --> Domain[领域模型层]
    Domain --> Repository[数据访问层]
    Repository --> Storage[(数据库 / 文件存储 / 检索服务 / 向量库)]
```

## 3. 核心模块设计

### 3.1 认证与权限模块

职责：

1. 登录。
2. 退出。
3. Token 校验。
4. 获取用户信息。
5. 角色权限管理。
6. 数据权限控制。

### 3.2 知识库模块

职责：

1. 知识分类管理。
2. **逻辑知识库**管理（绑定 Milvus `collection_name`、可选嵌入模型）。
3. 文档上传与元数据（可选 `kb_id`）；**Tika** 解析与 **异步分块**（`PENDING`/`RUNNING`/`SUCCESS`/`FAILED`）。
4. 文档权限控制（列表 SQL + 详情校验）。
5. 文档版本管理（规划能力，以库表与实现为准）。
6. 文档标题搜索等轻量检索（全文检索属扩展能力）。
7. **多切片**生成与维护、**Milvus** 向量同步（插入 / Upsert / 按文档或主键删除）。
8. **分块任务日志**查询。

> 接口与表结构见 **`docs/api.md`**、**`docs/database.md`**；实现总览见 **`docs/step3-summary.md`**。

### 3.3 Agent 智能检索模块（新增）

职责：

1. 接收用户自然语言对话。
2. 通过 LLM Agent 解析意图，识别 tool call。
3. 通过 MCP Server 暴露工具发现接口（`/mcp/sse`、`/mcp/tools/list`、`/mcp/messages`）。
4. 执行检索 Tool（搜索文档、列表文档、文档详情、知识库列表）。
5. 流式返回回复（SSE）。
6. 持久化会话与消息历史。
7. Tool 执行前做权限校验，只返回当前用户可见的文档级别信息。

> 定位：**纯检索助手**。不暴露管理操作（上传、分块、删除等）。
> 详细设计见 `docs/superpowers/specs/2026-05-10-agent-mcp-design.md`

### 3.4 智能问答模块

职责：

1. 接收用户问题。
2. 检索用户有权限访问的文档切片。
3. 生成有来源依据的答案。
4. 返回答案和引用来源。
5. 收集用户反馈。

### 3.5 会议预约模块

职责：

1. 会议室管理。
2. 会议预约。
3. 会议室冲突检测。
4. 参会人冲突检测。
5. 会议修改。
6. 会议取消。
7. 会议纪要。

### 3.6 待办事项模块

职责：

1. 个人待办管理。
2. 截止时间管理。
3. 提醒时间管理。
4. 优先级管理。
5. 完成状态管理。

### 3.7 任务协同模块

职责：

1. 任务创建。
2. 任务分配。
3. 状态流转。
4. 评论。
5. 结果提交。
6. 完成确认。

### 3.8 消息通知模块

职责：

1. 站内通知。
2. 未读数量统计。
3. 标记已读。
4. 跳转关联业务页面。

## 4. 关键流程图

### 4.1 Agent 智能检索流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant A as Agent 模块
    participant L as 大模型服务
    participant T as Tool Registry
    participant K as 知识库模块

    U->>A: 自然语言提问
    A->>A: 加载/创建会话
    A->>L: 发送 messages + tools
    L-->>A: tool_call: search_documents(...)
    A->>T: 路由到 SearchDocumentsTool
    T->>K: kbDocumentService.searchDocuments()
    K-->>T: 当前用户可见的文档列表
    T-->>A: ToolResult (过滤后)
    A->>L: 回填 tool_result
    L-->>A: text: "找到 3 篇相关文档..."
    A-->>U: SSE 流式回复
```

### 4.2 智能问答流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant Q as 智能问答模块
    participant P as 权限模块
    participant R as 检索服务
    participant L as 大模型服务
    participant DB as 数据库

    U->>Q: 提出问题
    Q->>P: 获取用户可访问知识范围
    P-->>Q: 返回权限过滤条件
    Q->>R: 检索文档切片
    R-->>Q: 返回候选切片
    Q->>L: 携带上下文生成答案
    L-->>Q: 返回答案
    Q->>DB: 保存问答记录
    Q-->>U: 返回答案和来源
```

### 4.2 会议预约流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant M as 会议模块
    participant C as 时间管理模块
    participant DB as 数据库
    participant N as 通知模块

    U->>M: 创建会议
    M->>DB: 检查会议室占用情况
    DB-->>M: 返回会议室可用性
    M->>C: 检查参会人时间冲突
    C-->>M: 返回冲突结果
    M->>DB: 保存会议
    M->>N: 发送会议通知
    N-->>U: 通知发送完成
```

## 5. 数据设计

完整数据库设计见 database.md。

## 6. 接口设计

完整接口设计见 api.md。

## 7. 安全设计

系统安全要求：

1. 所有业务接口默认需要认证。
2. 采用 RBAC 权限模型。
3. 支持数据权限过滤。
4. 支持文档权限过滤。
5. Agent Tool 执行前必须进行权限校验，返回结果必须按用户权限过滤。
6. 智能问答检索必须进行权限过滤。
7. 关键操作必须记录审计日志。
8. 密码不能明文存储。
9. 不允许泄露无权限文档来源。
10. Agent 返回结果不暴露 `content_text`、`file_url`、`chunk` 等内部字段。

## 8. 部署设计

完整部署方案见 deployment.md。
