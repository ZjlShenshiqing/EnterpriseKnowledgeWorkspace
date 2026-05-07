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
    Gateway --> AIQA[智能问答模块]
    Gateway --> Calendar[日历与时间管理模块]
    Gateway --> Meeting[会议预约模块]
    Gateway --> Todo[待办事项模块]
    Gateway --> Task[任务协同模块]
    Gateway --> Notify[消息通知模块]
    Gateway --> Dashboard[数据看板模块]
    Gateway --> System[系统管理模块]

    Knowledge --> FileStorage[文件存储]
    Knowledge --> Search[全文检索]
    Knowledge --> VectorDB[向量数据库]
    AIQA --> Search
    AIQA --> VectorDB
    AIQA --> LLM[大模型服务]

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

### 3.3 智能问答模块

职责：

1. 接收用户问题。
2. 检索用户有权限访问的文档切片。
3. 生成有来源依据的答案。
4. 返回答案和引用来源。
5. 收集用户反馈。

### 3.4 会议预约模块

职责：

1. 会议室管理。
2. 会议预约。
3. 会议室冲突检测。
4. 参会人冲突检测。
5. 会议修改。
6. 会议取消。
7. 会议纪要。

### 3.5 待办事项模块

职责：

1. 个人待办管理。
2. 截止时间管理。
3. 提醒时间管理。
4. 优先级管理。
5. 完成状态管理。

### 3.6 任务协同模块

职责：

1. 任务创建。
2. 任务分配。
3. 状态流转。
4. 评论。
5. 结果提交。
6. 完成确认。

### 3.7 消息通知模块

职责：

1. 站内通知。
2. 未读数量统计。
3. 标记已读。
4. 跳转关联业务页面。

## 4. 关键流程图

### 4.1 智能问答流程

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
5. 智能问答检索必须进行权限过滤。
6. 关键操作必须记录审计日志。
7. 密码不能明文存储。
8. 不允许泄露无权限文档来源。

## 8. 部署设计

完整部署方案见 deployment.md。
