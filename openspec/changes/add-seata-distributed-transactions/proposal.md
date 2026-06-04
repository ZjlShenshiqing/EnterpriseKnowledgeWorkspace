## Why

审批工作流已经开始跨越审批申请、流程实例、流程任务、待办/通知等多个业务边界，单服务本地事务无法覆盖后续跨服务写入的一致性风险。项目已经具备 Spring Cloud Alibaba、Nacos、MySQL、MyBatis-Plus 等基础条件，适合先引入 Seata 作为分布式事务基础设施，再以审批发起链路做最小真实闭环。

## What Changes

- 引入 Seata 分布式事务基础设施方案，明确 Seata Server、Nacos 注册/配置、事务分组、数据库存储和业务库 `undo_log` 的接入要求。
- 在业务服务中建立 Seata AT 模式接入规范，优先覆盖 MySQL + MyBatis-Plus 的跨服务同步写场景。
- 以审批工作流发起链路作为首个落地场景，定义全局事务入口、参与服务、回滚边界和失败验证要求。
- 明确非目标范围：Gateway 不参与业务全局事务；Milvus、OSS/S3、RocketMQ、模型调用等外部资源不通过 Seata AT 自动回滚，后续走 Outbox/Saga/补偿机制。
- 增加验证策略：通过故障注入确认第二个参与者失败时第一个参与者数据库写入被回滚。

## Capabilities

### New Capabilities

- `seata-distributed-transaction-infrastructure`: 定义 Seata Server、Nacos、事务分组、业务库 `undo_log`、客户端配置和服务接入规范。
- `approval-workflow-global-transaction`: 定义审批工作流发起链路使用 Seata AT 模式的一致性边界、回滚行为和验证要求。

### Modified Capabilities

- None.

## Impact

- 影响服务：
  - `enterprise-collaboration-service`：首个 Seata 客户端接入服务，承载审批工作流全局事务入口。
  - 后续真实参与审批待办/通知写入的服务：按实际调用边界接入 Seata 客户端。
  - `enterprise-gateway-service`：不作为全局事务参与者，仅继续承担路由、鉴权、身份透传。
- 影响依赖：
  - 新增 Spring Cloud Alibaba Seata starter 或等价 Seata Spring Boot 3 客户端依赖。
  - Seata Server 运行时依赖 MySQL 存储和 Nacos 注册/配置。
- 影响数据库：
  - 每个参与 AT 模式的业务库需要新增 `undo_log`。
  - Seata Server 事务状态库需要 `global_table`、`branch_table`、`lock_table`、`distributed_lock`。
- 影响配置：
  - Nacos 中新增 Seata Server 与客户端配置。
  - 参与服务新增 `seata.tx-service-group`、service vgroup mapping、registry/config 相关配置。
- 影响测试：
  - 增加审批发起链路的失败回滚验证。
  - 增加配置缺失、Seata 不可用时的启动/降级预期验证。
