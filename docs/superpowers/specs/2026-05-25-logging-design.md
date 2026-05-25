# 全模块日志系统设计

## 概述

为全部 4 个微服务 + 网关 + frameworks 建立统一日志体系：logback 文件输出 + AOP 请求日志 + 关键业务日志。

## 现状

- 248 个 Java 文件中仅 34 个（14%）使用 `@Slf4j`
- 网关 35 个类全无日志
- 无 logback 配置文件，日志仅输出到控制台
- 无 AOP 或拦截器级请求日志

## 架构

```
┌─────────────────────────────────────────┐
│  frameworks-web-starter                  │
│  ├─ ControllerLogAspect (AOP 切面)       │ ← 所有服务自动继承
│  └─ 敏感字段 mask 工具                   │
├─────────────────────────────────────────┤
│  每个模块                                 │
│  ├─ logback-spring.xml (文件输出配置)     │
│  ├─ Controller: @Slf4j + AOP 自动记录    │
│  └─ Service: @Slf4j + 手动关键日志       │
└─────────────────────────────────────────┘
```

## Logback 配置

每个模块 `src/main/resources/logback-spring.xml`，统一格式：

```xml
<!-- 日志格式 -->
<pattern>[%d{yyyy-MM-dd HH:mm:ss.SSS}] [%-5level] [%X{traceId}] [%logger{36}:%line] %msg%n</pattern>

<!-- 输出 -->
├── logs/{module}.log          ← 所有级别，按天滚动，保留 30 天，单文件最大 50MB
├── logs/{module}-error.log    ← ERROR 级别单独输出
└── 控制台                      ← dev profile 彩色输出
```

涉及模块：
- `enterprise-gateway-service`
- `enterprise-knowledge-ai-service`
- `enterprise-collaboration-service`
- `enterprise-workbench-service`

## AOP 请求日志（frameworks-web-starter）

在 `frameworks-web-starter` 新增 `ControllerLogAspect`，切所有 `@RestController` 和 `@Controller` 的 public 方法：

```
→ POST /api/docs {title:"xxx"}
← POST /api/docs 200 (32ms)
✗ POST /api/docs 500 (15ms) java.lang.RuntimeException: ...
```

功能：
- 记录 HTTP 方法 + 请求路径 + 入参 JSON（截断 500 字符）
- 记录响应状态码 + 耗时（毫秒）
- 异常时自动 `log.error` 含完整堆栈
- 自动 mask 敏感字段：`password`、`token`、`secret`、`accessToken`、`Authorization`
- 通过 `@ConditionalOnProperty` 控制开关，默认开启

切面注册为 `@Aspect` + `@Component`，在 `AutoConfiguration` 中通过 `@Import` 引入。

### Mask 规则

参数 JSON 中 Key 匹配以下模式时，value 替换为 `***`：
- `password`、`passwd`、`pwd`
- `token`、`accessToken`、`refreshToken`
- `secret`、`secretKey`
- `Authorization`

## 业务日志策略

### Controller 层

AOP 已覆盖入口/出口/异常，手动补：
- 权限拒绝时 `log.warn`
- 参数校验失败时 `log.warn`
- 业务分支关键路径 `log.info`

### Service 层

手动补关键操作：
- 创建/更新/删除：`log.info("创建文档: docId={}, userId={}", docId, userId)`
- 状态变更：`log.info("文档状态变更: docId={}, {} -> {}", docId, old, new)`
- 外部调用失败：`log.error("Milvus 写入失败: docId={}", docId, e)`
- 异常降级：`log.warn("OSS 未配置, 使用本地存储")`

### Gateway

| 类 | 日志内容 |
|---|---|
| JwtAuthenticationWebFilter | 认证通过（userId）/ 拒绝（IP + 原因） |
| SimpleRateLimitGlobalFilter | 限流触发（IP + 路径） |
| IdentityPropagationGlobalFilter | 身份头注入（userId → downstream） |
| IpAccessGlobalFilter | IP 黑/白名单拦截 |
| GatewayExceptionHandler | 路由转发异常（含目标服务 + 堆栈） |
| AuthController | 登录成功/失败 |
| SystemAdminController | 管理员操作审计 |

### 协作服务

| 类 | 日志内容 |
|---|---|
| DocController | 文档创建/删除 |
| DocOTService | OT 操作提交/冲突/快照 |
| DocWebSocketHandler | 连接建立/断开/订阅/权限拒绝 |
| DocPermissionService | 权限检查拒绝 |
| JwtAuthFilter | 认证通过/拒绝 |
| ChatWebSocketHandler | 连接建立/断开/消息发送 |

### 知识库服务

| 类 | 日志内容 |
|---|---|
| KbDocumentServiceImpl | 文档上传/删除/状态变更 |
| DocumentChunkingService | 分块开始/完成/失败 |
| MilvusVectorWriter | 向量写入成功/失败 |
| DocumentChunkEventListener | 异步分块任务触发 |

### 工作台服务

| 类 | 日志内容 |
|---|---|
| WorkbenchController | 聚合查询耗时/缓存命中 |

## 实施阶段

| 阶段 | 内容 | 预估 |
|------|------|------|
| 1 | logback-spring.xml × 4 模块 | 小 |
| 2 | ControllerLogAspect（frameworks-web-starter） | 中 |
| 3 | 网关日志补全（8 个关键类） | 小 |
| 4 | 协作服务日志补全（17 个 Controller + Service） | 中 |
| 5 | 知识库服务日志补全（关键 Service + Controller） | 中 |
| 6 | 工作台 + frameworks 日志补全 | 小 |

## 注意事项

- 不记录请求/响应 body 完整内容（避免日志膨胀和敏感数据泄露）
- 入参截断 500 字符
- 不在日志中记录 JWT token 原始值
- 文件日志按天滚动，保留 30 天，生产环境自动清理
