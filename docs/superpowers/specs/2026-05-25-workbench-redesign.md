# Workbench 服务职责重设计

## Context

当前 Workbench 服务没有明确职责，仅作为 collaboration + knowledge 的聚合层。Dashboard 数据同步依赖缓存和跨服务调用链路过长。需要给 Workbench 赋予独立职责，与 Collaboration 形成清晰边界。

## 职责划分

**Collaboration（协作服务 :8090）** —— 共享业务实体的唯一所有者
- 会议、待办、任务、审批的 CRUD
- IM 消息、WebSocket 连接

**Workbench（工作台服务 :8084）** —— "我"的个人视图
- 自有数据（写）：仪表盘布局、收藏、最近浏览
- 只读聚合（读）：从 collaboration / knowledge 拉取今日会议、待办等，不存储业务数据

Workbench 只读 collaboration 数据，不写。Collaboration 完全不知道 Workbench 存在。

## API 设计

### 自有数据（读写）

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | /api/workbench/layout | 获取我的仪表盘布局 |
| PUT | /api/workbench/layout | 保存布局 |
| GET | /api/workbench/favorites | 我的收藏列表 |
| POST | /api/workbench/favorites | 收藏条目 |
| DELETE | /api/workbench/favorites/{id} | 取消收藏 |

### 聚合查询（只读，Redis 缓存 5min）

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | /api/workbench/overview | 聚合快照：今日会议数、待办数、知识统计、最近文档 |
| GET | /api/workbench/my-meetings | 今日会议（透传 collab） |
| GET | /api/workbench/my-todos | 我的待办（透传 collab） |
| GET | /api/workbench/recent-docs | 最近文档（透传 knowledge） |

### 缓存

- Jackson JSON 序列化（非 JDK 序列化）
- TTL 5 分钟
- 编译器 `-parameters` 标志保留参数名

## 数据库

```sql
CREATE TABLE wb_user_layout (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT NOT NULL UNIQUE,
    layout_json TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE wb_favorite (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    item_type  VARCHAR(32) NOT NULL,   -- document / meeting / kb
    item_id    BIGINT NOT NULL,
    title      VARCHAR(256) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);
```

## 前端 Dashboard

- 移除 AI 性能指标（已完成）
- 会议/待办/文档卡片通过 workbench 聚合接口获取
- 布局和收藏功能后续迭代实现
