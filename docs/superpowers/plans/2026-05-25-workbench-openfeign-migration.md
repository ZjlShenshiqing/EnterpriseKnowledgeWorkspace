# Workbench OpenFeign 迁移 + Dashboard 数据修复 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 workbench 服务的服务间调用从 RestTemplate 迁移到 OpenFeign，修复 Dashboard 数据全零的 bug

**Architecture:** WorkbenchController 注入两个 Feign 客户端（CollaborationFeignClient、KnowledgeFeignClient）替代 RestTemplate 手写 URL 拼接；协作服务新增 `/api/chat/unread-count` 端点

**Tech Stack:** Spring Cloud OpenFeign, Nacos Discovery, Spring Cloud LoadBalancer（已有）

---

## File Structure

```
enterprise-workbench-service/
  pom.xml                                          ← 添加 spring-cloud-starter-openfeign
  src/main/java/com/zjl/workbench/
    WorkbenchApplication.java                      ← 添加 @EnableFeignClients
    config/
      RestTemplateConfig.java                      ← 删除（不再需要）
      FeignConfig.java                             ← 新建（日志配置）
    feign/
      CollaborationFeignClient.java                ← 新建（协作服务调用）
      KnowledgeFeignClient.java                    ← 新建（知识库服务调用）
    web/
      WorkbenchController.java                     ← 重构（Feign 替换 RestTemplate）

enterprise-collaboration-service/
  src/main/java/com/zjl/collaboration/web/
    ChatController.java                            ← 新增 GET /api/chat/unread-count
```

---

### Task 1: workbench pom.xml — 添加 OpenFeign 依赖

**Files:**
- Modify: `enterprise-workbench-service/pom.xml`

- [ ] **Step 1: 添加 spring-cloud-starter-openfeign 依赖**

在 `enterprise-workbench-service/pom.xml` 的 `<dependencies>` 中，`spring-cloud-starter-loadbalancer` 之后添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

插入位置参考（第 63 行 loadbalancer 依赖之后）。

- [ ] **Step 2: 验证编译**

```bash
mvn validate -pl enterprise-workbench-service
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-workbench-service/pom.xml
git commit -m "build: workbench 添加 spring-cloud-starter-openfeign 依赖

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: WorkbenchApplication — 启用 Feign

**Files:**
- Modify: `enterprise-workbench-service/src/main/java/com/zjl/workbench/WorkbenchApplication.java`

- [ ] **Step 1: 添加 @EnableFeignClients 注解**

修改 `WorkbenchApplication.java`：

```java
package com.zjl.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableCaching
@EnableFeignClients
@SpringBootApplication(scanBasePackages = {"com.zjl.workbench", "com.zjl.common"})
public class WorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkbenchApplication.class, args);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/WorkbenchApplication.java
git commit -m "feat: WorkbenchApplication 启用 @EnableFeignClients

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: 新建 FeignConfig — 日志配置

**Files:**
- Create: `enterprise-workbench-service/src/main/java/com/zjl/workbench/config/FeignConfig.java`

- [ ] **Step 1: 创建 FeignConfig**

```java
package com.zjl.workbench.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;

/**
 * OpenFeign 通用配置
 */
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/config/FeignConfig.java
git commit -m "feat: 添加 FeignConfig 基础日志配置

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: 新建 CollaborationFeignClient

**Files:**
- Create: `enterprise-workbench-service/src/main/java/com/zjl/workbench/feign/CollaborationFeignClient.java`

- [ ] **Step 1: 创建协作服务 Feign 客户端接口**

```java
package com.zjl.workbench.feign;

import com.zjl.common.response.Result;
import com.zjl.workbench.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 协作服务（enterprise-collaboration-service）Feign 客户端
 */
@FeignClient(name = "enterprise-collaboration-service", configuration = FeignConfig.class)
public interface CollaborationFeignClient {

    @GetMapping("/api/todos")
    Result<List<Map<String, Object>>> getTodos(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);

    @GetMapping("/api/meetings/my")
    Result<List<Map<String, Object>>> getMyMeetings(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam(defaultValue = "") String userName);

    @GetMapping("/api/approvals")
    Result<List<Map<String, Object>>> getApprovals(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);

    @GetMapping("/api/tasks")
    Result<List<Map<String, Object>>> getTasks(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);

    @GetMapping("/api/intents")
    Result<Map<String, Object>> getIntents(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);

    @GetMapping("/api/chat/unread-count")
    Result<Integer> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);
}
```

注意：`/api/meetings/my` 通过 `userName` 查询参数区分用户，与前端 Meetings 页面保持一致。

- [ ] **Step 2: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/feign/CollaborationFeignClient.java
git commit -m "feat: 新建 CollaborationFeignClient — 协作服务调用

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: 新建 KnowledgeFeignClient

**Files:**
- Create: `enterprise-workbench-service/src/main/java/com/zjl/workbench/feign/KnowledgeFeignClient.java`

- [ ] **Step 1: 创建知识库服务 Feign 客户端接口**

```java
package com.zjl.workbench.feign;

import com.zjl.common.response.Result;
import com.zjl.workbench.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 知识库服务（enterprise-knowledge-ai-service）Feign 客户端
 */
@FeignClient(name = "enterprise-knowledge-ai-service", configuration = FeignConfig.class)
public interface KnowledgeFeignClient {

    @GetMapping("/api/kb/documents")
    Result<Map<String, Object>> getDocuments(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);

    @GetMapping("/api/kb/bases")
    Result<Map<String, Object>> getBases(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);

    @GetMapping("/api/kb/agent/sessions")
    Result<Map<String, Object>> getAgentSessions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/feign/KnowledgeFeignClient.java
git commit -m "feat: 新建 KnowledgeFeignClient — 知识库服务调用

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: 协作服务 ChatController — 新增 /api/chat/unread-count

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatController.java`

- [ ] **Step 1: 在 ChatController 添加 unread-count 端点**

在 `ChatController.java` 的 `conversations()` 方法之后（第 67 行后）插入：

```java
@GetMapping("/unread-count")
public Result<Integer> unreadCount(@RequestHeader("X-User-Id") Long userId) {
    List<Long> convIds = memberMapper.selectList(
            Wrappers.lambdaQuery(ImConversationMember.class)
                    .eq(ImConversationMember::getUserId, userId))
            .stream().map(ImConversationMember::getConversationId).toList();
    if (convIds.isEmpty()) return Results.success(0);
    int total = 0;
    for (Long convId : convIds) {
        total += readService.unreadCount(userId, convId);
    }
    return Results.success(total);
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatController.java
git commit -m "feat: ChatController 新增 GET /api/chat/unread-count 端点

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: 重构 WorkbenchController — Feign 替换 RestTemplate

**Files:**
- Modify: `enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java`

- [ ] **Step 1: 用 Feign 客户端重写 overview() 方法**

完整替换 `WorkbenchController.java`：

```java
package com.zjl.workbench.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.workbench.entity.WbFavorite;
import com.zjl.workbench.feign.CollaborationFeignClient;
import com.zjl.workbench.feign.KnowledgeFeignClient;
import com.zjl.workbench.mapper.WbFavoriteMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final CollaborationFeignClient collabClient;
    private final KnowledgeFeignClient knowledgeClient;
    private final WbFavoriteMapper favoriteMapper;

    private static final String UA = "X-User-Id";
    private static final String AD = "X-Is-Admin";

    public WorkbenchController(CollaborationFeignClient collabClient,
                               KnowledgeFeignClient knowledgeClient,
                               WbFavoriteMapper favoriteMapper) {
        this.collabClient = collabClient;
        this.knowledgeClient = knowledgeClient;
        this.favoriteMapper = favoriteMapper;
    }

    @GetMapping("/overview")
    @Cacheable(value = "wb_overview", key = "#userId", unless = "#result.data.isEmpty()")
    public Result<Map<String, Object>> overview(
            @RequestHeader(UA) Long userId,
            @RequestHeader(value = AD, defaultValue = "false") String isAdmin) {

        Map<String, Object> data = new LinkedHashMap<>();

        // 待办列表
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTodos(userId, isAdmin);
            List<Map<String, Object>> todos = resp != null ? resp.getData() : List.of();
            data.put("todos", todos != null ? todos : List.of());
        } catch (FeignException e) {
            log.warn("获取待办失败: {}", e.getMessage());
            data.put("todos", List.of());
        }

        // 会议（使用 /api/meetings/my 与会议页保持一致）
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getMyMeetings(userId, isAdmin, "");
            List<Map<String, Object>> meetings = resp != null ? resp.getData() : List.of();
            if (meetings == null) meetings = List.of();
            var today = java.time.LocalDate.now().toString();
            var todayMeetings = meetings.stream()
                    .filter(m -> today.equals(String.valueOf(m.get("date"))))
                    .toList();
            data.put("meetings", meetings);
            data.put("todayMeetings", todayMeetings);
            data.put("meetingCount", todayMeetings.size());
        } catch (FeignException e) {
            log.warn("获取会议失败: {}", e.getMessage());
            data.put("meetings", List.of());
            data.put("todayMeetings", List.of());
            data.put("meetingCount", 0);
        }

        // 最近文档（知识库）
        try {
            Result<Map<String, Object>> resp = knowledgeClient.getDocuments(userId, isAdmin, 1, 5);
            if (resp != null && resp.getData() != null) {
                Map<String, Object> kbData = resp.getData();
                Object records = kbData.get("records");
                if (records instanceof List list) {
                    List<Map<String, Object>> docs = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map m) {
                            Map<String, Object> doc = new LinkedHashMap<>(m);
                            doc.put("docType", "knowledge");
                            docs.add(doc);
                        }
                    }
                    data.put("recentDocs", docs);
                    data.put("documentCount", kbData.getOrDefault("total", 0));
                } else {
                    data.put("recentDocs", List.of());
                    data.put("documentCount", 0);
                }
            } else {
                data.put("recentDocs", List.of());
                data.put("documentCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取文档失败: {}", e.getMessage());
            data.put("recentDocs", List.of());
            data.put("documentCount", 0);
        }

        // 待办数量
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTodos(userId, isAdmin);
            List<Map<String, Object>> todoList = resp != null ? resp.getData() : List.of();
            if (todoList == null) todoList = List.of();
            data.put("todoCount", todoList.stream().filter(t -> {
                Object d = t.get("done");
                return d == null || "0".equals(d.toString()) || Boolean.FALSE.equals(d);
            }).count());
        } catch (FeignException e) {
            log.warn("获取待办统计失败: {}", e.getMessage());
            data.put("todoCount", 0);
        }

        // 任务进行中数量
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTasks(userId, isAdmin);
            List<Map<String, Object>> tasks = resp != null ? resp.getData() : List.of();
            if (tasks == null) tasks = List.of();
            data.put("inProgressTaskCount", tasks.stream().filter(t -> {
                Object s = t.get("status");
                return "todo".equals(s) || "in_progress".equals(s);
            }).count());
        } catch (FeignException e) {
            log.warn("获取任务统计失败: {}", e.getMessage());
            data.put("inProgressTaskCount", 0);
        }

        // 审批统计
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getApprovals(userId, isAdmin);
            List<Map<String, Object>> approvals = resp != null ? resp.getData() : List.of();
            if (approvals == null) approvals = List.of();
            data.put("pendingApprovalCount", approvals.stream().filter(a -> {
                Object s = a.get("status");
                return s == null || (!"approved".equals(s.toString()) && !"rejected".equals(s.toString()));
            }).count());
        } catch (FeignException e) {
            log.warn("获取审批统计失败: {}", e.getMessage());
            data.put("pendingApprovalCount", 0);
        }

        // 未读消息数
        try {
            Result<Integer> resp = collabClient.getUnreadCount(userId, isAdmin);
            data.put("unreadMessageCount", resp != null && resp.getData() != null ? resp.getData() : 0);
        } catch (FeignException e) {
            log.warn("获取未读消息数失败: {}", e.getMessage());
            data.put("unreadMessageCount", 0);
        }

        // 知识库数量
        try {
            Result<Map<String, Object>> resp = knowledgeClient.getBases(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map basesData) {
                data.put("baseCount", basesData.getOrDefault("total", 0));
            } else {
                data.put("baseCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取知识库统计失败: {}", e.getMessage());
            data.put("baseCount", 0);
        }

        // 意图配置数量
        try {
            Result<Map<String, Object>> resp = collabClient.getIntents(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map intentsData) {
                data.put("intentCount", intentsData.getOrDefault("total", 0));
            } else {
                data.put("intentCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取意图统计失败: {}", e.getMessage());
            data.put("intentCount", 0);
        }

        // 今日会话数量
        try {
            Result<Map<String, Object>> resp = knowledgeClient.getAgentSessions(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map sessionsData) {
                data.put("todaySessionCount", sessionsData.getOrDefault("total", 0));
            } else {
                data.put("todaySessionCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取会话统计失败: {}", e.getMessage());
            data.put("todaySessionCount", 0);
        }

        return Results.success(data);
    }

    @GetMapping("/stats")
    @Cacheable(value = "wb_stats", key = "'global'", unless = "#result.data.isEmpty()")
    public Result<Map<String, Object>> stats(
            @RequestHeader(UA) Long userId,
            @RequestHeader(value = AD, defaultValue = "false") String isAdmin) {

        Map<String, Object> data = new LinkedHashMap<>();

        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTasks(userId, isAdmin);
            List<Map<String, Object>> tasks = resp != null ? resp.getData() : List.of();
            if (tasks == null) tasks = List.of();
            long todo = 0, inProgress = 0, review = 0, done = 0;
            for (var t : tasks) {
                String s = String.valueOf(t.get("status"));
                switch (s) {
                    case "todo" -> todo++;
                    case "in_progress" -> inProgress++;
                    case "review" -> review++;
                    case "done" -> done++;
                }
            }
            data.put("taskStats", Map.of("todo", todo, "inProgress", inProgress,
                    "review", review, "done", done, "total", tasks.size()));
        } catch (FeignException e) {
            log.warn("获取任务统计失败: {}", e.getMessage());
            data.put("taskStats", Map.of());
        }

        try {
            Result<List<Map<String, Object>>> resp = collabClient.getApprovals(userId, isAdmin);
            List<Map<String, Object>> approvals = resp != null ? resp.getData() : List.of();
            if (approvals == null) approvals = List.of();
            long pending = 0, approved = 0, rejected = 0;
            for (var a : approvals) {
                String s = String.valueOf(a.get("status"));
                if ("approved".equals(s)) approved++;
                else if ("rejected".equals(s)) rejected++;
                else pending++;
            }
            data.put("approvalStats", Map.of("pending", pending, "approved", approved,
                    "rejected", rejected, "total", approvals.size()));
        } catch (FeignException e) {
            log.warn("获取审批统计失败: {}", e.getMessage());
            data.put("approvalStats", Map.of());
        }

        try {
            Result<List<Map<String, Object>>> resp = collabClient.getMyMeetings(userId, isAdmin, "");
            List<Map<String, Object>> meetings = resp != null ? resp.getData() : List.of();
            if (meetings == null) meetings = List.of();
            var today = java.time.LocalDate.now().toString();
            long todayMeetings = meetings.stream()
                    .filter(m -> today.equals(String.valueOf(m.get("date"))))
                    .count();
            data.put("meetingStats", Map.of("today", todayMeetings, "total", meetings.size()));
        } catch (FeignException e) {
            log.warn("获取会议统计失败: {}", e.getMessage());
            data.put("meetingStats", Map.of());
        }

        try {
            Result<Map<String, Object>> resp = knowledgeClient.getDocuments(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map kbData) {
                data.put("docCount", kbData.getOrDefault("total", 0));
            } else {
                data.put("docCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取文档统计失败: {}", e.getMessage());
            data.put("docCount", 0);
        }

        return Results.success(data);
    }

    @GetMapping("/favorites")
    public Result<List<WbFavorite>> listFavorites(@RequestHeader(UA) Long userId) {
        List<WbFavorite> list = favoriteMapper.selectList(
                new LambdaQueryWrapper<WbFavorite>().eq(WbFavorite::getUserId, userId)
                        .orderByDesc(WbFavorite::getCreatedAt));
        return Results.success(list);
    }

    @PostMapping("/favorites")
    public Result<WbFavorite> addFavorite(@RequestHeader(UA) Long userId,
                                          @RequestBody Map<String, Object> body) {
        WbFavorite f = new WbFavorite();
        f.setUserId(userId);
        f.setItemType((String) body.get("itemType"));
        f.setItemId(Long.valueOf(body.get("itemId").toString()));
        f.setTitle((String) body.get("title"));
        favoriteMapper.insert(f);
        return Results.success(f);
    }

    @DeleteMapping("/favorites/{id}")
    public Result<Void> removeFavorite(@RequestHeader(UA) Long userId,
                                       @PathVariable Long id) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<WbFavorite>()
                        .eq(WbFavorite::getId, id)
                        .eq(WbFavorite::getUserId, userId));
        return Results.success();
    }

    @DeleteMapping("/cache")
    @CacheEvict(value = {"wb_overview", "wb_stats"}, allEntries = true)
    public Result<Void> clearCache() {
        return Results.success();
    }

    @DeleteMapping("/cache/user/{userId}")
    @CacheEvict(value = "wb_overview", key = "#userId")
    public Result<Void> clearUserCache(@PathVariable Long userId) {
        return Results.success();
    }
}
```

关键变更点：
- 构造函数注入 `CollaborationFeignClient` + `KnowledgeFeignClient`，移除 `RestTemplate`、`collabUrl`、`knowledgeUrl`
- 移除 `callList()`、`callForObject()`、`toHttpHeaders()` 三个私有方法
- 所有下游调用改为 `collabClient.xxx()` / `knowledgeClient.xxx()`
- `/api/meetings` → `/api/meetings/my`（修复会议不一致）
- `/api/im/unread-count` → `/api/chat/unread-count`（修复 404）
- catch `Exception` → catch `FeignException`，并添加 `log.warn` 日志

- [ ] **Step 2: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java
git commit -m "refactor: WorkbenchController 改用 OpenFeign 替换 RestTemplate

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 8: 删除 RestTemplateConfig

**Files:**
- Delete: `enterprise-workbench-service/src/main/java/com/zjl/workbench/config/RestTemplateConfig.java`

- [ ] **Step 1: 删除文件**

```bash
rm enterprise-workbench-service/src/main/java/com/zjl/workbench/config/RestTemplateConfig.java
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/config/RestTemplateConfig.java
git commit -m "refactor: 删除 RestTemplateConfig，已迁移至 OpenFeign

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 9: 编译验证

**Files:**
- 无新建或修改，仅验证

- [ ] **Step 1: 编译整个项目**

```bash
mvn compile -pl enterprise-workbench-service,enterprise-collaboration-service -am -DskipTests
```

Expected: BUILD SUCCESS，无编译错误。

- [ ] **Step 2: 运行已有测试**

```bash
mvn test -pl enterprise-workbench-service,enterprise-collaboration-service
```

Expected: 所有已有测试通过。

---

## Verification Checklist

代码变更完成后，启动服务验证：

1. Dashboard 页面——管理员视图：`collabStats`（待办、今日会议、待审批、未读消息）不再全为 0
2. Dashboard 页面——普通用户视图：四张协同统计卡片数值正确
3. 今日会议列表展示正确的会议数据
4. 点击刷新按钮，缓存清除后数据正常加载
5. 停止协作服务后，dashboard 降级显示 0 而非报错
6. `/api/workbench/stats` 端点正常返回统计数据
