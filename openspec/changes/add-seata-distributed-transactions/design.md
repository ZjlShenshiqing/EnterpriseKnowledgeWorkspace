## Context

当前项目是 Spring Boot 3.4.4 多模块微服务系统，使用 Spring Cloud Alibaba 2025.0.0.0 与 Nacos 做服务发现和配置管理。业务服务使用 MySQL；`enterprise-collaboration-service`、`enterprise-knowledge-ai-service`、`enterprise-platform-admin-service` 主要使用 MyBatis-Plus，`enterprise-gateway-service` 使用 JPA 管理认证与权限数据。

审批工作流是当前最适合接入分布式事务的业务场景。一次“发起审批”可能同时创建审批申请、流程实例、流程任务，并进一步写入待办/通知/工作台数据。一旦这些写入跨服务或跨库，单服务本地事务无法保证整体一致。

Seata 第一阶段只用于同步的关系型数据库写入场景。Milvus、OSS/S3、RocketMQ、WebSocket、AI 模型调用等外部副作用不能依赖 Seata AT 自动回滚，需要通过状态机、Outbox、重试或补偿流程处理。

## Goals / Non-Goals

**Goals:**

- 引入 Seata 作为 MySQL 业务服务的分布式事务基础设施。
- 第一阶段默认使用 Seata AT 模式，覆盖 MySQL + MyBatis-Plus 的跨服务同步写场景。
- 明确 Seata Server、Nacos 注册/配置、事务分组、数据库存储表、业务库 `undo_log` 的要求。
- 明确审批工作流发起链路作为第一个真实全局事务场景。
- 在方案中定义失败注入验证，确认参与者失败时前置数据库写入能够回滚。

**Non-Goals:**

- 不把 `enterprise-gateway-service` 放进业务全局事务。
- 不使用 Seata AT 回滚 Milvus、OSS/S3、RocketMQ、WebSocket、模型调用等外部资源。
- 不在本变更中重构所有服务边界。
- 不在第一阶段实现 TCC 或 Saga，除非后续针对具体业务另立方案。
- 不为只读接口接入分布式事务。

## Decisions

### Decision 1: 第一阶段采用 AT 模式

第一阶段使用 Seata AT 模式。原因是当前候选链路以 MySQL 写入为主，持久层是 JDBC/MyBatis-Plus，AT 模式对业务代码侵入较低，可以覆盖审批发起的一致性问题。

备选方案：

- TCC：第一阶段不采用。TCC 需要显式设计 Try/Confirm/Cancel 业务接口，会显著增加审批创建链路复杂度。
- Saga：第一阶段不采用。审批创建属于短事务同步 DB 写入，Saga 更适合后续文档处理、向量化、外部系统调用等长流程。
- XA：第一阶段不采用。XA 的锁持有和协调成本更高，当前首个场景没有必要。

### Decision 2: Gateway 不参与全局事务

`enterprise-gateway-service` 继续作为路由、鉴权和身份透传边界。它不能承载 `@GlobalTransactional` 业务方法，也不应该成为业务事务参与者。全局事务入口必须放在真正拥有业务写入的应用服务层。

### Decision 3: 首个事务入口放在协作服务

`enterprise-collaboration-service` 是第一阶段最合适的事务发起服务，因为审批工作流创建逻辑在该服务内。全局事务边界应放在“提交审批”这一应用服务方法上，而不是 Controller 或 Mapper 上。

### Decision 4: 只给真实参与者接入 Seata

只有发起或参与首个全局事务的服务才添加 Seata 客户端依赖、数据源代理和 `undo_log`。不要在没有跨服务写入边界前把所有服务都接入 Seata，避免无意义的数据源代理和启动配置风险。

### Decision 5: 外部副作用从 AT 事务中剥离

审批后的 RocketMQ、WebSocket、OSS/S3、Milvus、AI 模型调用等副作用不能作为 AT 回滚对象。此类操作必须在数据库事务提交后执行，或者通过 Outbox/补偿机制表达。

### Decision 6: Seata Server 使用 MySQL 存储并注册到 Nacos

Seata Server 使用 DB 模式保存事务状态，并通过 Nacos 注册和读取配置。这与当前项目基础设施一致，也方便排查全局事务、分支事务和全局锁记录。

## Risks / Trade-offs

- Seata 版本与 Spring Boot 3.4.4、Spring Cloud Alibaba 2025.0.0.0 可能存在兼容风险 → 先固定版本并用最小服务启动验证，再进入业务链路。
- AT 模式依赖 SQL 解析和主键 → 参与表必须有稳定主键，并避免复杂或不支持的 SQL 写法。
- 缺少 `undo_log` 会导致运行时回滚失败 → 每个参与业务库必须先执行迁移脚本并验证表存在。
- 全局事务过长会增加锁竞争 → 审批发起事务必须保持短小，通知和外部调用放到提交后。
- Spring 自调用可能绕过事务代理 → `@GlobalTransactional` 必须放在外部调用进入的应用服务方法上，避免 private/internal self invocation 作为入口。
- Seata Server 不可用会影响强一致写链路 → 生产环境需要监控 Seata Server，并明确强一致链路在不可用时失败而不是静默降级。
- 本地事务与全局事务边界混用容易误解 → 文档和代码中明确哪些方法是全局事务入口，哪些方法只是本地参与者。

## Migration Plan

1. 准备 Seata Server 数据库表：`global_table`、`branch_table`、`lock_table`、`distributed_lock`。
2. 配置 Seata Server 的 Nacos 注册和配置中心，并定义项目事务分组。
3. 给第一个参与业务库增加 `undo_log`，从协作服务数据库开始。
4. 给 `enterprise-collaboration-service` 添加 Seata 客户端依赖和配置。
5. 如果审批首个全局事务调用另一个写服务，再给该服务添加 Seata 客户端依赖、配置和 `undo_log`。
6. 在审批提交应用服务方法上添加第一个 `@GlobalTransactional` 边界。
7. 增加失败注入验证：下游参与者抛异常时，所有参与库写入回滚。
8. 先在开发环境验证，再进入测试/预发，最后生产启用。

回滚策略：

- 移除或关闭 `@GlobalTransactional` 入口，使流程退回本地事务行为。
- 保留 `undo_log` 表；没有 Seata 参与时该表不会影响业务。
- 如果 Seata Server 不可用且没有批准降级方案，需要阻断依赖强一致的写链路。

## Open Questions

- 最终待办/工作台记录归属哪个服务：`enterprise-collaboration-service`、`enterprise-platform-admin-service`，还是后续独立 workbench 服务？
- 生产环境 Seata Server 采用独立部署、Docker Compose 组件，还是纳入现有部署平台？
- 与 Spring Boot 3.4.4、Spring Cloud Alibaba 2025.0.0.0 最终兼容的 Seata 客户端版本需要在实现阶段验证后固定。
- 通知创建第一阶段是同步数据库写入，还是作为 after-commit/outbox 副作用处理？
