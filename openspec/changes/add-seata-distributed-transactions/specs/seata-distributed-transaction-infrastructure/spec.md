## ADDED Requirements

### Requirement: Seata Server 注册与配置
系统 SHALL 提供 Seata Server 部署配置，使 Seata Server 能够注册到 Nacos，并从 Nacos 或批准的本地开发配置中读取运行配置。

#### Scenario: Seata Server 成功注册
- **WHEN** Seata Server 使用项目 Nacos 配置启动
- **THEN** Nacos 服务发现中 SHALL 能看到可用的 Seata Server 实例

#### Scenario: Seata Server 配置可解析
- **WHEN** Seata Server 启动
- **THEN** 它 SHALL 能解析 store mode、service group mapping、registry 等运行配置

### Requirement: Seata Server 数据库存储
系统 SHALL 在非本地环境使用 MySQL 保存 Seata Server 的事务状态。

#### Scenario: 事务状态表存在
- **WHEN** Seata Server 使用 DB store mode 启动
- **THEN** Seata Server 数据库 SHALL 包含 `global_table`、`branch_table`、`lock_table`、`distributed_lock`

#### Scenario: 事务状态可持久化
- **WHEN** 全局事务开始并创建分支事务
- **THEN** Seata Server SHALL 将全局事务、分支事务和锁记录写入配置的事务状态库

### Requirement: 业务库 undo_log
每个参与 Seata AT 模式的 MySQL 业务库 SHALL 包含与选定 Seata 版本兼容的 `undo_log` 表。

#### Scenario: 协作库参与全局事务
- **WHEN** `enterprise-collaboration-service` 参与 Seata AT 全局事务
- **THEN** 其业务数据库 SHALL 包含兼容的 `undo_log` 表

#### Scenario: 新增参与服务
- **WHEN** 另一个服务被加入 Seata AT 全局事务
- **THEN** 该服务数据库 SHALL 在服务启用 Seata 前完成 `undo_log` 迁移

### Requirement: Seata 客户端配置
每个启用 Seata 的业务服务 SHALL 定义事务服务分组和 service vgroup mapping，并能解析到配置的 Seata Server 集群。

#### Scenario: 客户端解析事务分组
- **WHEN** 启用 Seata 的服务启动
- **THEN** 服务 SHALL 成功解析 `seata.tx-service-group` 及其对应的 service vgroup mapping

#### Scenario: 多服务使用兼容事务分组
- **WHEN** 多个服务参与同一个全局事务
- **THEN** 它们 SHALL 使用同一项目环境下兼容的事务分组映射

### Requirement: Seata 客户端接入范围控制
只有发起或参与已批准全局事务的服务 SHALL 引入 Seata 客户端依赖和数据源代理。

#### Scenario: Gateway 不参与业务事务
- **WHEN** 前端请求进入 `enterprise-gateway-service`
- **THEN** Gateway SHALL 只执行路由、鉴权和身份透传，不成为 Seata 全局事务参与者

#### Scenario: 非参与服务保持不变
- **WHEN** 某服务没有已批准的跨服务写入角色
- **THEN** 该服务 SHALL 不需要引入 Seata 客户端依赖或 `undo_log`

### Requirement: 外部副作用不纳入 AT 回滚
系统 SHALL 不把非数据库外部副作用视为可由 Seata AT 自动回滚。

#### Scenario: 存在外部副作用
- **WHEN** 业务流程需要调用 RocketMQ、WebSocket、Milvus、OSS/S3 或 AI 模型接口
- **THEN** 该副作用 SHALL 在数据库事务提交后执行，或通过明确的 Outbox/补偿方案处理

#### Scenario: 外部副作用失败
- **WHEN** 外部副作用在数据库全局事务提交后失败
- **THEN** 系统 SHALL 通过重试、补偿或状态跟踪处理，而不是期待 Seata AT 回滚
