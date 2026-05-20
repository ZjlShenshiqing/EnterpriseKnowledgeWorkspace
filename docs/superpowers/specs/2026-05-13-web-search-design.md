# 联网搜索功能设计文档

> 基于 2026-05-13 讨论，为智能知识问答添加联网搜索能力。

---

## 1. 概述

当前"智能知识问答"页面的"联网搜索"pill 为纯展示元素，无任何交互。本次设计为后端新增 `WebSearchTool`，前端将 pill 改为可切换的 toggle 按钮，用户可手动控制是否允许 AI 搜索互联网。

### 核心交互

```
用户开启"联网搜索" → 发送消息 → 后端 AgentLoop 注册 web_search 工具
                                  → LLM 判断是否需要 → 调用博查 API
                                  → 结果回填 → LLM 综合回答
用户关闭"联网搜索" → 发送消息 → 后端仅注册内部知识库工具（现有行为）
```

---

## 2. 后端设计

### 2.1 新增配置

```yaml
app.agent.web-search:
  enabled: true
  api-key: ${BOCHA_API_KEY:}
  base-url: https://api.bochaai.com/v1
  count: 8
  freshness: noLimit
```

**配置属性类**：`com.zjl.knowledge.agent.config.AgentProperties` 新增内部类 `WebSearch`，前缀 `app.agent.web-search`。

### 2.2 新增 BochaWebSearchClient

**文件路径**：`agent/search/BochaWebSearchClient.java`

```
POST https://api.bochaai.com/v1/web-search
Authorization: Bearer <api-key>
Content-Type: application/json
Body: { query, freshness, summary: true, count }
```

- 使用 Java 11 `HttpClient`（无需额外依赖）
- 连接超时 10s，读取超时 30s
- 搜索无结果返回空列表，不抛异常
- 网络异常 / 非 2xx 响应 → `ToolResult.fail()`

### 2.3 新增 WebSearchTool 实现 McpTool

**文件路径**：`agent/tool/WebSearchTool.java`

- tool 名称：`web_search`
- tool 描述：搜索互联网获取最新公开信息，返回网页标题、URL、摘要
- 入参：`query`（搜索关键词）
- 执行：调用 `BochaWebSearchClient`，将结果拼接为文本返回
- 无 API Key 时执行返回 "Web search API key not configured"

### 2.4 修改 AgentLoop

- `run()` 方法和 `agentLoop()` 方法新增 `boolean webSearch` 参数
- `webSearch=true` 时，额外将 `web_search` 注册到工具列表
- 系统提示中注明：web_search 仅在用户明确开启时可用

### 2.5 修改 AgentController → ChatRequest

`ChatRequest` 新增字段：
```java
private boolean webSearch;  // 默认 false
```

---

## 3. 前端设计

### 3.1 api/index.js

```js
export function agentChat(sessionId, message, webSearch = false) {
  return fetch('/api/kb/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ sessionId, message, webSearch })
  })
}
```

### 3.2 Chat.vue

- 新增 `const webSearchOn = ref(false)`
- pill 绑定 `@click="webSearchOn = !webSearchOn"`，添加 `cursor: pointer`
- 开启态样式：蓝色渐变边框 + `#eff6ff` 底色 + `#1d4ed8` 文字
- 关闭态样式：现有灰色样式
- 首页 landing 和对话线程底部输入区两处同步
- `send()` 中将 `webSearchOn.value` 传给 `agentChat`

---

## 4. 数据流

```
Chat.vue (webSearchOn=true)
  → agentChat(sessionId, message, true)
    → POST /api/kb/agent/chat { sessionId, message, webSearch: true }
      → AgentController.chat(request)
        → AgentLoop.run(session, user, emitter, webSearch=true)
          → agentLoop(): tools = [search_documents, rag_qa, ..., web_search]
            → LLM 判断需要联网
              → ToolRegistry.execute("web_search", {query})
                → WebSearchTool.execute()
                  → BochaWebSearchClient.search(query)
                    → POST https://api.bochaai.com/v1/web-search
                  ← { results: [...] }
                ← ToolResult.success(formattedText)
              ← LLM 处理搜索结果
            ← LLM 综合回答
          → SSE 流式输出
        ← SseEmitter
      ← SSE stream
    ← ReadableStream
  → 渲染回答
```

---

## 5. 边界情况

| 情况 | 处理 |
|------|------|
| API Key 未配置 | 工具执行返回友好提示，不中断对话 |
| 博查 API 不可达 / 超时 | 捕获异常 → `ToolResult.fail("网络搜索暂时不可用")` |
| 搜索结果为空 | 返回 "未找到相关网页"，LLM 仅基于内部知识库回答 |
| 博查返回非 2xx | 读取错误体 → `ToolResult.fail()` |
| 搜索关键词为空 | 工具层面校验，返回 `ToolResult.fail()` |
| webSearch=false | 不注册 web_search 工具，前端 pill 灰色关闭态 |

## 6. 不做的

- 不缓存搜索结果
- 不计费统计（博查控制台可查看）
- 不限制用户搜索频率
- 不修改系统提示词中的角色定位（联网搜索是辅助能力，不改变助手定位）
