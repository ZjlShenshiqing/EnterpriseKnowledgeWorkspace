# 联网搜索功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为智能知识问答实现联网搜索——前端 toggle 控制，后端通过博查 API 新增 WebSearchTool，AgentLoop 条件注册工具。

**Architecture:** 复用现有 McpTool 体系，新增 WebSearchTool + BochaWebSearchClient。前端 Chat.vue "联网搜索" pill 改为 toggle，状态随请求传给后端。AgentLoop 根据状态决定是否注册 web_search 工具。

**Tech Stack:** Java 17 HttpClient, Spring Boot 3.3.5, Vue 3, 博查 Web Search API

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 创建 | `agent/search/BochaWebSearchClient.java` | 调用博查 API，封装 HTTP 请求 |
| 创建 | `agent/tool/WebSearchTool.java` | McpTool 实现，将搜索结果格式化为 LLM 可读文本 |
| 修改 | `agent/config/AgentProperties.java` | 新增 WebSearch 内部配置类 |
| 修改 | `agent/AgentLoop.java` | 新增 webSearch 参数，条件注册工具 |
| 修改 | `agent/AgentController.java` | ChatRequest 新增 webSearch 字段 |
| 修改 | `src/main/resources/application.yml` | 新增 web-search 配置段 |
| 修改 | `enterprise-web/src/api/index.js` | agentChat 新增 webSearch 参数 |
| 修改 | `enterprise-web/src/pages/Chat.vue` | pill 改为 toggle，传递状态 |

---

### Task 1: AgentProperties 新增 WebSearch 配置类

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/config/AgentProperties.java`

- [ ] **Step 1: 新增 WebSearch 内部类**

在 `AgentProperties` 类中添加新字段和内部类：

```java
/** 联网搜索配置 */
private WebSearch webSearch = new WebSearch();

@Data
public static class WebSearch {

    /** 是否启用联网搜索 */
    private boolean enabled = true;

    /** 博查 API Key */
    private String apiKey = "";

    /** 博查 API 地址 */
    private String baseUrl = "https://api.bochaai.com/v1";

    /** 每次搜索返回条数 */
    private int count = 8;

    /** 时间范围：noLimit / oneDay / oneWeek / oneMonth / oneYear */
    private String freshness = "noLimit";
}
```

完整修改位置：在 `AgentProperties` 类的 `Session` 内部类之后添加 `webSearch` 字段和 `WebSearch` 内部类。

- [ ] **Step 2: 编译验证**

```bash
cd enterprise-knowledge-ai-service && mvn compile -pl . -q
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/config/AgentProperties.java
git commit -m "feat: AgentProperties 新增 WebSearch 配置类"
```

---

### Task 2: application.yml 新增 web-search 配置

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/resources/application.yml`

- [ ] **Step 1: 添加配置**

在 `app.agent.session` 配置块之后（`archive-after-days: 30` 之后，空行后）添加：

```yaml
    web-search:
      enabled: true
      api-key: ${BOCHA_API_KEY:}
      base-url: https://api.bochaai.com/v1
      count: 8
      freshness: noLimit
```

缩进级别：4 个空格（与 `llm:` 和 `session:` 同级）。

- [ ] **Step 2: 验证配置绑定**

```bash
cd enterprise-knowledge-ai-service && mvn compile -pl . -q
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/resources/application.yml
git commit -m "feat: application.yml 新增 web-search 配置段"
```

---

### Task 3: 新增 BochaWebSearchClient

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/search/BochaWebSearchClient.java`

- [ ] **Step 1: 创建 search 包目录**

```bash
mkdir -p enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/search
```

- [ ] **Step 2: 编写 BochaWebSearchClient**

```java
package com.zjl.knowledge.agent.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 博查 Web Search API 客户端
 */
@Slf4j
@Component
public class BochaWebSearchClient {

    private final AgentProperties.WebSearch config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BochaWebSearchClient(AgentProperties agentProperties) {
        this.config = agentProperties.getWebSearch();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行网络搜索
     *
     * @param query 搜索关键词
     * @return 搜索结果列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String query) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("博查 API Key 未配置，跳过搜索");
            return Collections.emptyList();
        }

        try {
            Map<String, Object> body = Map.of(
                    "query", query,
                    "freshness", config.getFreshness(),
                    "summary", true,
                    "count", config.getCount()
            );

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/web-search"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("博查 API 返回非 200: status={}, body={}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Object results = result.get("results");
            if (results instanceof List) {
                return (List<Map<String, Object>>) results;
            }
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("博查 API 调用失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd enterprise-knowledge-ai-service && mvn compile -pl . -q
```

预期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/search/
git commit -m "feat: 新增 BochaWebSearchClient 博查 API 客户端"
```

---

### Task 4: 新增 WebSearchTool

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/tool/WebSearchTool.java`

- [ ] **Step 1: 编写 WebSearchTool**

```java
package com.zjl.knowledge.agent.tool;

import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.agent.search.BochaWebSearchClient;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索 Tool —— 调用博查 API 搜索互联网
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements McpTool {

    private final BochaWebSearchClient bochaWebSearchClient;
    private final AgentProperties agentProperties;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("web_search")
                .description("搜索互联网获取最新公开信息。"
                        + "适用场景：用户询问最新新闻、行业动态、外部技术问题等内部知识库无法回答的问题。"
                        + "返回网页标题、URL、摘要。"
                        + "仅在用户明确开启联网搜索时可用。")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("query"))
                        .properties(new LinkedHashMap<>() {{
                            put("query", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("搜索关键词，支持自然语言")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("搜索关键词不能为空");
        }

        if (agentProperties.getWebSearch().getApiKey() == null
                || agentProperties.getWebSearch().getApiKey().isBlank()) {
            return ToolResult.failure("Web search API key not configured");
        }

        List<Map<String, Object>> results = bochaWebSearchClient.search(query);

        if (results.isEmpty()) {
            return ToolResult.success("未找到相关网页。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("网络搜索结果：\n\n");
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> r = results.get(i);
            sb.append(i + 1).append(". **")
                    .append(r.getOrDefault("title", "无标题")).append("**\n");
            sb.append("   URL: ").append(r.getOrDefault("url", "")).append("\n");
            Object summary = r.get("summary");
            if (summary != null && !summary.toString().isBlank()) {
                sb.append("   摘要: ").append(summary).append("\n");
            } else {
                sb.append("   摘要: ").append(r.getOrDefault("snippet", "")).append("\n");
            }
            sb.append("\n");
        }

        return ToolResult.success(sb.toString());
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd enterprise-knowledge-ai-service && mvn compile -pl . -q
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/tool/WebSearchTool.java
git commit -m "feat: 新增 WebSearchTool 联网搜索工具"
```

---

### Task 5: 修改 AgentLoop 支持条件注册 web_search

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/AgentLoop.java`

- [ ] **Step 1: 修改 run 方法签名和 agentLoop 方法**

把 `run()` 方法和 `agentLoop()` 方法新增 `boolean webSearch` 参数：

```java
public void run(KbAgentSession session, UserContext user, AgentSseEmitter emitter, boolean webSearch) {
    try {
        agentLoop(session, user, emitter, webSearch);
    } catch (Exception e) {
        log.error("Agent 循环异常, sessionId={}", session.getId(), e);
        emitter.error(e.getMessage());
    }
}

private void agentLoop(KbAgentSession session, UserContext user, AgentSseEmitter emitter, boolean webSearch) {
```

- [ ] **Step 2: 修改 tools 获取逻辑**

将 `agentLoop` 中：

```java
// ② 获取 tools
List<ToolDefinition> tools = toolRegistry.getAllDefinitions();
```

改为：

```java
// ② 获取 tools（联网搜索开启时包含 web_search）
List<ToolDefinition> tools;
if (webSearch) {
    tools = toolRegistry.getAllDefinitions();
} else {
    tools = toolRegistry.getAllDefinitions().stream()
            .filter(t -> !"web_search".equals(t.getName()))
            .toList();
}
```

- [ ] **Step 3: 编译验证**

```bash
cd enterprise-knowledge-ai-service && mvn compile -pl . -q
```

预期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/AgentLoop.java
git commit -m "feat: AgentLoop 支持条件注册 web_search 工具"
```

---

### Task 6: 修改 AgentController 传递 webSearch 参数

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/AgentController.java`

- [ ] **Step 1: ChatRequest 新增 webSearch 字段**

在 `ChatRequest` 内部类中添加：

```java
@lombok.Data
public static class ChatRequest {
    private Long sessionId;
    private String message;
    private boolean webSearch;
}
```

- [ ] **Step 2: 传递 webSearch 到 AgentLoop**

将 `chat()` 方法中：

```java
agentLoop.run(session, user, emitter);
```

改为：

```java
agentLoop.run(session, user, emitter, request.isWebSearch());
```

- [ ] **Step 3: 编译验证**

```bash
cd enterprise-knowledge-ai-service && mvn compile -pl . -q
```

预期：BUILD SUCCESS

- [ ] **Step 4: 启动验证**

```bash
cd enterprise-knowledge-ai-service && mvn spring-boot:run -pl . &
# 等待启动后 curl 测试
curl -X POST http://localhost:8083/api/kb/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"message":"test","webSearch":false}'
```

预期：返回 SSE 流式响应

- [ ] **Step 5: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/AgentController.java
git commit -m "feat: AgentController 传递 webSearch 参数到 AgentLoop"
```

---

### Task 7: 前端 api/index.js 新增 webSearch 参数

**Files:**
- Modify: `enterprise-web/src/api/index.js`

- [ ] **Step 1: 修改 agentChat 函数**

```js
export function agentChat(sessionId, message, webSearch = false) {
  return fetch('/api/kb/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ sessionId, message, webSearch })
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-web/src/api/index.js
git commit -m "feat: agentChat 新增 webSearch 参数"
```

---

### Task 8: 前端 Chat.vue 联网搜索 toggle 实现

**Files:**
- Modify: `enterprise-web/src/pages/Chat.vue`

- [ ] **Step 1: script 中新增 webSearchOn 响应式变量**

在 `<script setup>` 顶部，`const sending = ref(false)` 之后添加：

```js
const webSearchOn = ref(false)
```

- [ ] **Step 2: 修改两处 pill 模板**

将 landing 区域（约第 48-53 行）和 thread 输入区（约第 165-170 行）的两处：

```html
<span class="pill-web">
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20"/>
  </svg>
  联网搜索
</span>
```

改为：

```html
<span
  class="pill-web"
  :class="{ 'pill-web--on': webSearchOn }"
  @click="webSearchOn = !webSearchOn"
  style="cursor: pointer"
  :title="webSearchOn ? '已开启联网搜索' : '点击开启联网搜索'"
>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20"/>
  </svg>
  联网搜索
</span>
```

- [ ] **Step 3: 修改 send() 方法传递 webSearchOn**

将 `send()` 方法中：

```js
const resp = await agentChat(null, text)
```

改为：

```js
const resp = await agentChat(null, text, webSearchOn.value)
```

- [ ] **Step 4: 新增 toggle 激活态样式**

在 `<style>` 中 `.pill-web` 样式块之后添加：

```css
.pill-web--on {
  border-color: #818cf8;
  background: #eff6ff;
  color: #1d4ed8;
}
```

- [ ] **Step 5: 启动前端验证**

```bash
cd enterprise-web && npm run dev
```

打开浏览器访问 `/chat`，验证：
- 点击"联网搜索" pill 可在开启/关闭之间切换
- 开启态：蓝色边框 + 浅蓝底色 + 蓝色文字
- 关闭态：灰色样式（不变）
- 发送消息时 Network 面板确认请求体包含 `webSearch: true/false`

- [ ] **Step 6: Commit**

```bash
git add enterprise-web/src/pages/Chat.vue
git commit -m "feat: Chat.vue 联网搜索 pill 改为功能 toggle"
```
