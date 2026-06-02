## ADDED Requirements

### Requirement: Workbench overview aggregation

Gateway SHALL 在 `/api/workbench/overview` 端点聚合来自协作服务和知识库服务的数据，为前端仪表盘提供统一的概览数据。

聚合内容包括：
- 待办列表（来自 Collaboration `/api/todos`）
- 今日会议（来自 Collaboration `/api/meetings`，按 date 过滤今天）
- 最近文档（来自 Knowledge-AI `/api/kb/documents`，取前 5 条）
- 待办未完成数量、进行中任务数量、待审批数量
- 未读消息数、知识库数量、意图配置数量、今日会话数

聚合失败时，对应字段返回空列表或 0，不影响其他字段。

#### Scenario: Successful overview aggregation
- **WHEN** 用户 GET `/api/workbench/overview` 携带 `X-User-Id` 头
- **THEN** 系统并发调用协作和知识库服务的多个端点，组装后返回统一的 overview JSON

#### Scenario: Partial downstream failure
- **WHEN** 协作服务的 `/api/todos` 端点超时或返回错误
- **THEN** todos 字段返回 `[]`，todoCount 返回 `0`，其他字段正常返回

### Requirement: Workbench stats aggregation

Gateway SHALL 在 `/api/workbench/stats` 端点提供全局统计数据聚合。

聚合内容包括：
- 任务统计（todo/inProgress/review/done 计数）
- 审批统计（pending/approved/rejected 计数）
- 会议统计（今日会议数/总会数）
- 文档总数

#### Scenario: Stats aggregation
- **WHEN** 用户 GET `/api/workbench/stats` 携带 `X-User-Id` 头
- **THEN** 系统返回按类别分组的统计数据

### Requirement: Overview and stats caching

Gateway SHALL 对 overview 和 stats 端点启用缓存。

- overview 缓存 key 为用户 ID，过期时间通过配置控制
- stats 缓存 key 为全局，过期时间通过配置控制
- 下游调用失败时 SHALL NOT 缓存降级结果

#### Scenario: Cache hit for overview
- **WHEN** 同一用户在缓存有效期内再次请求 `/api/workbench/overview`
- **THEN** 系统直接返回缓存数据，不调用下游服务
