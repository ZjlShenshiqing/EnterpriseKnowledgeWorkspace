## 1. 版本与基础设施确认

- [ ] 1.1 确认与 Spring Boot 3.4.4、Spring Cloud Alibaba 2025.0.0.0 兼容的 Seata Server 与客户端版本。
- [ ] 1.2 明确 Seata Server 本地开发启动方式，并记录启动命令、端口、Nacos 地址和 MySQL store 配置。
- [ ] 1.3 定义项目事务分组名称，例如 `enterprise-workspace-tx-group`，并统一写入 Seata Server 与客户端配置。
- [ ] 1.4 准备 Seata Server 数据库初始化脚本，包含 `global_table`、`branch_table`、`lock_table`、`distributed_lock`。

## 2. 数据库迁移

- [ ] 2.1 为协作服务业务库编写 `undo_log` 增量迁移脚本。
- [ ] 2.2 如果审批发起链路需要调用第二个写服务，为该服务业务库编写 `undo_log` 增量迁移脚本。
- [ ] 2.3 按项目数据库规范更新完整 schema 文件 `resouces/enterprise_knowledge_workspace.sql`，使其反映最新结构。
- [ ] 2.4 验证参与 AT 模式的核心业务表均具备稳定主键。

## 3. Seata 客户端接入

- [ ] 3.1 在 `enterprise-collaboration-service` 中加入 Seata 客户端依赖。
- [ ] 3.2 在 `enterprise-collaboration-service` 配置 `seata.tx-service-group`、Nacos registry/config、service vgroup mapping。
- [ ] 3.3 验证协作服务启动时数据源被 Seata 正确代理，且 MyBatis-Plus 正常工作。
- [ ] 3.4 若存在第二个审批写入参与服务，为该服务重复依赖、配置和启动验证。
- [ ] 3.5 确认 `enterprise-gateway-service` 不添加 Seata 客户端依赖，不参与业务全局事务。

## 4. 审批工作流全局事务实现

- [ ] 4.1 梳理审批发起链路中的数据库写入点：审批申请、流程实例、流程任务、待办/通知或工作台记录。
- [ ] 4.2 将 `@GlobalTransactional` 放到协作服务应用服务层的审批提交入口方法。
- [ ] 4.3 保留参与服务内部的本地事务，使其作为 Seata 分支事务提交或回滚。
- [ ] 4.4 将 RocketMQ、WebSocket、外部通知等副作用调整为事务提交后执行，或明确放入后续 Outbox/补偿方案。
- [ ] 4.5 避免通过 private 方法或同类 self invocation 作为全局事务入口。

## 5. 验证与测试

- [ ] 5.1 增加成功路径测试：审批提交后，审批申请、流程实例、流程任务和下游参与数据均成功提交。
- [ ] 5.2 增加失败注入测试：下游参与者抛异常后，协作服务和下游服务相关数据库写入均回滚。
- [ ] 5.3 增加本地写入失败测试：流程实例或流程任务写入失败后，审批申请不留下成功记录。
- [ ] 5.4 验证 Seata Server 不可用时强一致写链路的失败行为，不允许静默成功。
- [ ] 5.5 运行 `mvn test -pl enterprise-collaboration-service -am` 并确认通过。

## 6. 文档与运维

- [ ] 6.1 在项目文档中补充 Seata 本地启动、Nacos 配置、数据库脚本和排障说明。
- [ ] 6.2 记录哪些方法是全局事务入口，哪些服务是第一阶段 Seata 参与者。
- [ ] 6.3 记录非 AT 范围：Milvus、OSS/S3、RocketMQ、WebSocket、模型调用需要 Outbox/Saga/补偿。
- [ ] 6.4 补充上线检查清单：Seata Server 可用、Nacos 配置存在、`undo_log` 存在、事务分组一致。
