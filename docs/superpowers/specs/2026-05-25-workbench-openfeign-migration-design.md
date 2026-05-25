# Workbench 服务 OpenFeign 迁移 & Dashboard 数据不一致修复

## 问题背景

Dashboard 页面第一列统计数据全部显示 0，今日会议为空。根因分析：

1. `/api/im/unread-count` 端点在协作服务中**不存在**，每次返回 404，未读消息数始终为 0
2. 工作台 `/overview` 调用 `/api/meetings`，而会议页面使用 `/api/meetings/my`，两者行为不一致
3. WorkbenchController 通过 RestTemplate 手写 URL 拼接调用下游服务，出错时静默吞异常返回 0，排查困难

## 方案

将 workbench 服务的服务间调用从 RestTemplate 迁移到 OpenFeign，同时修复上述具体问题。

## 变更范围

### 模块一：workbench 服务（主要变更）

**pom.xml** — 添加依赖：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

**启动类** — 添加 `@EnableFeignClients`

**新建 Feign 接口：**

- `CollaborationFeignClient` — 调用 enterprise-collaboration-service
  - `GET /api/todos` → `Result<List<Map<String,Object>>>`
  - `GET /api/meetings/my` → `Result<List<Map<String,Object>>>`（修复：原为 `/api/meetings`）
  - `GET /api/approvals` → `Result<List<Map<String,Object>>>`
  - `GET /api/tasks` → `Result<List<Map<String,Object>>>`
  - `GET /api/intents?current=1&size=1` → `Result<Map<String,Object>>`
  - `GET /api/chat/unread-count` → `Result<Integer>`（修复：原端点为不存在的 `/api/im/unread-count`）

- `KnowledgeFeignClient` — 调用 enterprise-knowledge-ai-service
  - `GET /api/kb/documents?current=1&size=5` → `Result<Map<String,Object>>`
  - `GET /api/kb/bases?current=1&size=1` → `Result<Map<String,Object>>`
  - `GET /api/kb/agent/sessions?current=1&size=1` → `Result<Map<String,Object>>`

**Feign 配置：**
- 添加 Feign 日志级别配置，便于排查调用失败
- 添加 Fallback 降级：服务不可用时返回空列表和 0，行为与当前一致但更规范

**WorkbenchController 重构：**
- 注入 `CollaborationFeignClient` 和 `KnowledgeFeignClient`
- 移除 `RestTemplate`、`collabUrl`、`knowledgeUrl` 字段
- 移除 `callList()`、`callForObject()`、`toHttpHeaders()` 三个私有方法
- 各个 try-catch 改为检查 `Result.code` + catch `FeignException`

**可删除：**
- `RestTemplateConfig.java` — RestTemplate Bean 不再需要

### 模块二：协作服务（小改动）

**ChatController** — 新增端点：
- `GET /api/chat/unread-count?userId={userId}` → `Result<Integer>`
- 逻辑：遍历用户所有会话，累加每个会话的 unread 计数

### 不变

- 前端 `Dashboard.vue` 完全不动
- `/api/workbench/overview` 响应 JSON 格式保持兼容
- `/api/workbench/stats` 同步重构，格式不变
- 其他服务（gateway、knowledge）不涉及

## 错误处理策略

| 场景 | 当前行为 | Feign 后 |
|------|---------|---------|
| 下游服务不可达 | RestTemplate 抛异常 → catch → 返回 0 | Fallback 降级 → 返回空列表/0 |
| 下游返回非 200 | 静默返回 0 | 检查 Result.code 或 Fallback |
| 网络超时 | 静默返回 0 | Feign 默认超时 + Fallback |

## 测试要点

1. Dashboard 页面统计数据正确显示（非零值）
2. 今日会议列表正确展示
3. 停止协作服务后，dashboard 降级显示 0 而非报错
4. 刷新按钮（清除缓存）仍然正常工作
