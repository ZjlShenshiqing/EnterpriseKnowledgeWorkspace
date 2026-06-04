# Seata 分布式事务 — 运维手册

## 1. Seata Server 本地启动

### 环境要求
- Java 17+
- Nacos 运行中（localhost:8848）
- MySQL 运行中

### 初始化数据库
```bash
mysql -u root -p < openspec/changes/add-seata-distributed-transactions/seata-server-db.sql
```

### 启动 Seata Server
```bash
# 下载 Seata Server 2.2+ (与 Spring Cloud Alibaba 2025.0.0.0 兼容)
# 修改 conf/application.yml 的 registry 和 config 指向 Nacos
# 启动
sh bin/seata-server.sh -p 8091 -h 127.0.0.1
```

### Nacos 配置

在 Nacos 中为 `DEFAULT_GROUP` 添加 `seataServer.properties`：

```properties
store.mode=db
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.cj.jdbc.Driver
store.db.url=jdbc:mysql://localhost:3306/seata?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
store.db.user=root
store.db.password=123456
service.vgroupMapping.enterprise-workspace-tx-group=default
```

## 2. 数据库脚本

### Seata Server 事务状态库
- 脚本位置：`openspec/changes/add-seata-distributed-transactions/seata-server-db.sql`
- 包含：`global_table`、`branch_table`、`lock_table`、`distributed_lock`

### 业务库 undo_log
- 协作服务迁移脚本：`enterprise-collaboration-service/src/main/resources/db/migration/011-undo-log.sql`
- 全量 schema：`resouces/enterprise_knowledge_workspace.sql` 已包含 `undo_log`

## 3. 全局事务入口

| 方法 | 位置 | 说明 |
|------|------|------|
| `ApprovalApplicationServiceImpl.create()` | `enterprise-collaboration-service` | 审批发起全局事务入口，timeout=300s |

### 第一阶段 Seata 参与者
- **enterprise-collaboration-service** — 审批发起方，承载 `@GlobalTransactional`
- 后续待办/工作台服务按实际跨服务调用边界接入

## 4. 非 AT 范围

以下资源不通过 Seata AT 自动回滚，需要 Outbox / Saga / 补偿：

| 资源 | 原因 | 策略 |
|------|------|------|
| Milvus 向量存储 | 非 JDBC 资源 | Outbox + 重试 |
| OSS / S3 文件存储 | 非 JDBC 资源 | 补偿清除 |
| RocketMQ 消息 | 不能在事务内发送 | after-commit + 重试 |
| WebSocket 推送 | 非数据库操作 | after-commit 触发 |
| AI 模型调用 | 无回滚语义 | 状态标记 + 重试 |

## 5. 排障

### 问题：启动报 "can not register RM"
- 检查 Seata Server 是否已启动且注册到 Nacos
- 检查 Nacos 中 `seataServer.properties` 配置存在
- 检查 `seata.tx-service-group` 与 Nacos 中的 vgroup mapping 一致

### 问题：数据库写入未回滚
- 检查业务库中 `undo_log` 表是否存在
- 检查业务表是否有主键（AT 模式要求主键）
- 检查 Seata Server 日志中分支事务状态

### 问题：Seata Server 不可用时的影响
- 强一致写链路（审批提交）会因为无法注册分支事务而失败
- 不会静默降级为本地事务
- 读接口不受影响

## 6. 上线检查清单

- [ ] Seata Server 已启动并注册到 Nacos（`localhost:8848/nacos` 服务列表可查）
- [ ] Nacos 中存在 `seataServer.properties` 配置
- [ ] Seata Server 数据库中 `global_table`、`branch_table`、`lock_table`、`distributed_lock` 四张表已创建
- [ ] 协作服务数据库 `enterprise_collaboration` 中 `undo_log` 表已创建
- [ ] 协作服务 `application.yml` 中事务分组为 `enterprise-workspace-tx-group`
- [ ] `seata.tx-service-group` 与 Nacos 中的 `service.vgroupMapping` 一致
- [ ] 参与 AT 模式的核心业务表（`sys_approval_request`、`wf_instance`、`wf_task`、`wf_record`）均有主键
- [ ] `enterprise-gateway-service` 未引入 Seata 依赖
- [ ] 测试环境已执行审批发起的成功路径和失败回滚验证
