## ADDED Requirements

### Requirement: 审批发起全局事务入口
系统 SHALL 将审批发起链路的全局事务入口放在 `enterprise-collaboration-service` 的应用服务层方法上，而不是 Controller、Mapper 或 Gateway 上。

#### Scenario: 用户发起审批
- **WHEN** 用户提交审批申请
- **THEN** 协作服务 SHALL 在应用服务层开启 Seata 全局事务并完成审批申请、流程实例、流程任务等数据库写入

#### Scenario: Gateway 转发审批请求
- **WHEN** 请求经由 Gateway 到达协作服务
- **THEN** Gateway SHALL 不创建全局事务，只透传用户身份和路由请求

### Requirement: 审批发起链路回滚一致性
系统 SHALL 保证审批发起全局事务内任一数据库参与者失败时，所有已参与的数据库写入整体回滚。

#### Scenario: 协作服务本地写入失败
- **WHEN** 审批申请、流程实例或流程任务任一写入失败
- **THEN** 全局事务 SHALL 回滚协作服务内已完成的相关写入

#### Scenario: 下游参与服务失败
- **WHEN** 审批发起链路调用下游 Seata 参与服务且下游服务抛出异常
- **THEN** 全局事务 SHALL 回滚协作服务和下游参与服务中属于本次全局事务的数据库写入

### Requirement: 本地事务作为全局事务分支
审批发起链路中每个参与服务 SHALL 使用本地事务管理本服务数据库写入，并作为 Seata 全局事务的分支事务参与提交或回滚。

#### Scenario: 分支事务提交
- **WHEN** 审批发起全局事务内所有参与者成功
- **THEN** 每个服务的本地事务 SHALL 随全局事务提交

#### Scenario: 分支事务回滚
- **WHEN** 审批发起全局事务内任一参与者失败
- **THEN** 每个已参与服务的本地事务 SHALL 随全局事务回滚

### Requirement: 审批外部副作用延后处理
审批发起链路中的通知、消息推送、RocketMQ 事件、WebSocket 推送等外部副作用 SHALL 不在 Seata AT 全局事务内直接执行。

#### Scenario: 审批事务提交成功
- **WHEN** 审批发起全局事务提交成功
- **THEN** 系统 SHALL 在提交后触发通知、消息推送或事件发送

#### Scenario: 审批事务回滚
- **WHEN** 审批发起全局事务回滚
- **THEN** 系统 SHALL 不发送表示审批已创建成功的外部通知或事件

### Requirement: 审批全局事务失败验证
系统 SHALL 提供可重复执行的验证方式，证明审批发起链路在参与者失败时能够回滚。

#### Scenario: 故障注入验证
- **WHEN** 测试环境启用一个下游参与者失败开关并提交审批
- **THEN** 测试 SHALL 验证审批申请、流程实例、流程任务和下游参与者数据均未留下本次提交的成功记录

#### Scenario: 成功路径验证
- **WHEN** 测试环境关闭失败开关并提交审批
- **THEN** 测试 SHALL 验证审批申请、流程实例、流程任务和下游参与者数据均按预期提交
