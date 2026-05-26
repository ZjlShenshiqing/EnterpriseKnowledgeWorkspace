# Workbench 服务职责重设计 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 Workbench 服务赋予独立职责——管理用户个人视图（布局、收藏），从 collaboration/knowledge 只读聚合 Dashboard 数据。

**Architecture:** Workbench 新增自有表 `wb_user_layout` 和 `wb_favorite`，提供布局/收藏的 CRUD API。聚合接口（overview 等）通过 RestTemplate 调用 collaboration 和 knowledge 服务，结果用 Jackson 序列化缓存到 Redis。前端 Dashboard 通过 `/api/workbench/overview` 获取聚合数据。

**Tech Stack:** Spring Boot 3.4.4, MyBatis-Plus 3.5.7, Redis (缓存), Nacos (配置), Vue 3 + Vite (前端)

**当前已完成：**
- Redis 缓存配置（Jackson 序列化，5min TTL）
- `-parameters` 编译标志
- Nacos `collab.service.url` 指到 `localhost:8090`
- 前端 Dashboard 移除 AI 性能面板

---

### Task 1: 修复 knowledge 服务调用

**Files:**
- Modify: Nacos `enterprise-workbench-service.yaml`

**为什么：** Workbench 当前调用 knowledge 服务用 `http://enterprise-knowledge-ai-service` 服务名，Nacos LoadBalancer 解析可能有问题。加上直接 URL。

- [ ] **Step 1: 更新 Nacos workbench 配置，添加 knowledge URL**

```bash
curl -s -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  -d "dataId=enterprise-workbench-service.yaml" \
  -d "group=DEFAULT_GROUP" \
  -d "type=yaml" \
  -d "content=spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/enterprise_collaboration?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: \${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
collab:
  service:
    url: http://localhost:8090
knowledge:
  service:
    url: http://localhost:8083"
```

- [ ] **Step 2: 重启 workbench 服务，验证聚合**

```bash
curl -s http://localhost:8084/api/workbench/overview -H "X-User-Id:1" -H "X-Is-Admin:true" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, indent=2, ensure_ascii=False)[:1000])"
```

期望：`code=200`，`docCount` 和 `recentDocs` 不再为空。

- [ ] **Step 3: 验证缓存正常（连续两次请求，第二次走缓存）**

```bash
for i in 1 2; do
  curl -s -o /dev/null -w "Request $i: HTTP %{http_code}, time %{time_total}s\n" \
    http://localhost:8084/api/workbench/overview -H "X-User-Id:1" -H "X-Is-Admin:true"
done
```

期望：两次都 200，第二次显著快于第一次。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "fix: workbench 聚合 knowledge 服务 URL 修复，验证 overview 接口"
```

---

### Task 2: 创建 workbench 自有数据库表

**Files:**
- Create: `enterprise-workbench-service/src/main/resources/db/migration/V001__workbench.sql`

- [ ] **Step 1: 创建 migration 目录**

```bash
mkdir -p /Users/zjl/projectByZhangjilin/EnterpriseKnowledgeWorkspace/enterprise-workbench-service/src/main/resources/db/migration
```

- [ ] **Step 2: 编写 migration SQL**

```sql
-- V001__workbench.sql
CREATE TABLE IF NOT EXISTS wb_user_layout (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE,
    layout_json TEXT NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台布局';

CREATE TABLE IF NOT EXISTS wb_favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    item_type   VARCHAR(32) NOT NULL COMMENT 'document / meeting / kb',
    item_id     BIGINT NOT NULL,
    title       VARCHAR(256) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wb_fav_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏';
```

- [ ] **Step 3: 手动执行 SQL**

```bash
mysql -u root -p123456 enterprise_collaboration < /Users/zjl/projectByZhangjilin/EnterpriseKnowledgeWorkspace/enterprise-workbench-service/src/main/resources/db/migration/V001__workbench.sql
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: workbench 自有表 wb_user_layout + wb_favorite"
```

---

### Task 3: 创建 WbFavorite 实体和 Mapper

**Files:**
- Create: `enterprise-workbench-service/src/main/java/com/zjl/workbench/entity/WbFavorite.java`
- Create: `enterprise-workbench-service/src/main/java/com/zjl/workbench/mapper/WbFavoriteMapper.java`

- [ ] **Step 1: 创建实体类**

```java
package com.zjl.workbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wb_favorite")
public class WbFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String itemType;

    private Long itemId;

    private String title;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 创建 Mapper 接口**

```java
package com.zjl.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.workbench.entity.WbFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WbFavoriteMapper extends BaseMapper<WbFavorite> {
}
```

- [ ] **Step 3: 编译验证**

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn compile -pl enterprise-workbench-service -q
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: WbFavorite 实体和 Mapper"
```

---

### Task 4: WorkbenchController 添加布局和收藏 API

**Files:**
- Modify: `enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java`

- [ ] **Step 1: 添加依赖注入和收藏 API**

在 `WorkbenchController` 中注入 `WbFavoriteMapper` 和 `WbUserLayoutMapper`（即使 layout mapper 还没创建，先用 Map 存 JSON 代替）：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.workbench.entity.WbFavorite;
import com.zjl.workbench.mapper.WbFavoriteMapper;

// 在字段区域新增
private final WbFavoriteMapper favoriteMapper;

// 修改构造函数
public WorkbenchController(RestTemplate restTemplate, WbFavoriteMapper favoriteMapper) {
    this.rt = restTemplate;
    this.favoriteMapper = favoriteMapper;
}

// 在 @GetMapping("/stats") 方法之后新增收藏 API

@GetMapping("/favorites")
public Result<List<WbFavorite>> listFavorites(@RequestHeader(UA) Long userId) {
    var list = favoriteMapper.selectList(
            new LambdaQueryWrapper<WbFavorite>().eq(WbFavorite::getUserId, userId)
                    .orderByDesc(WbFavorite::getCreatedAt));
    return Results.success(list);
}

@PostMapping("/favorites")
public Result<WbFavorite> addFavorite(@RequestHeader(UA) Long userId, @RequestBody Map<String,Object> body) {
    WbFavorite f = new WbFavorite();
    f.setUserId(userId);
    f.setItemType((String) body.get("itemType"));
    f.setItemId(Long.valueOf(body.get("itemId").toString()));
    f.setTitle((String) body.get("title"));
    favoriteMapper.insert(f);
    return Results.success(f);
}

@DeleteMapping("/favorites/{id}")
public Result<Void> removeFavorite(@RequestHeader(UA) Long userId, @PathVariable Long id) {
    favoriteMapper.delete(
            new LambdaQueryWrapper<WbFavorite>()
                    .eq(WbFavorite::getId, id)
                    .eq(WbFavorite::getUserId, userId));
    return Results.success();
}
```

- [ ] **Step 2: 编译验证**

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn compile -pl enterprise-workbench-service -q
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: workbench 收藏 API (favorites CRUD)"
```

---

### Task 5: Dashboard.vue 前端适配

**Files:**
- Modify: `enterprise-web/src/pages/Dashboard.vue`
- Modify: `enterprise-web/src/api/index.js`

- [ ] **Step 1: 添加收藏 API 函数 (api/index.js)**

在 `api/index.js` 末尾添加：

```js
export function getFavorites() {
  return fetch('/api/workbench/favorites', { headers: getAuthHeaders() }).then(r => r.json())
}

export function addFavorite(body) {
  return fetch('/api/workbench/favorites', {
    method: 'POST',
    headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then(r => r.json())
}

export function removeFavorite(id) {
  return fetch(`/api/workbench/favorites/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders()
  }).then(r => r.json())
}
```

- [ ] **Step 2: Dashboard.vue 确认会议数据正确显示**

当前 `loadData()` 中的的过滤逻辑：

```js
if (data.meetings) todayMeetings.value = data.meetings.filter(m => m.date === new Date().toISOString().split('T')[0])
```

这是正确的——只显示今日会议。确认 todayMeetings 面板在有今日会议时正常渲染。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: 前端收藏 API + Dashboard 今日会议"
```

---

### Task 6: 端到端验证

- [ ] **Step 1: 在 Meetings 页面创建一个今天日期的会议**

打开 `http://localhost:5175/meetings`，新建一个今天 5 月 25 日的会议。

- [ ] **Step 2: 回到 Dashboard 验证**

打开 `http://localhost:5175/`（工作台），确认"今日会议"面板显示刚创建的会议。

- [ ] **Step 3: 测试收藏功能**

用 curl 测试：

```bash
# 收藏一个条目
curl -s -X POST http://localhost:8084/api/workbench/favorites \
  -H "Content-Type: application/json" \
  -H "X-User-Id:1" \
  -d '{"itemType":"document","itemId":1,"title":"测试文档"}' | python3 -m json.tool

# 获取收藏列表
curl -s http://localhost:8084/api/workbench/favorites -H "X-User-Id:1" | python3 -m json.tool

# 删除收藏
curl -s -X DELETE http://localhost:8084/api/workbench/favorites/1 -H "X-User-Id:1"
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: 端到端验证通过"
```

---

### Task 7: 收藏功能前端 UI（Optional）

**Files:**
- Modify: `enterprise-web/src/pages/Dashboard.vue`

- [ ] **Step 1: 在"最近文档"列表中每个文档后加收藏按钮**

在 `doc-item` 的 `doc-info` 后面加：

```html
<span class="doc-fav" @click.stop="toggleFav(doc)">★</span>
```

加对应的 toggleFav 方法和收藏状态管理。

如果时间有限，此 task 可延后——先确保后端 API 和数据库就绪。
