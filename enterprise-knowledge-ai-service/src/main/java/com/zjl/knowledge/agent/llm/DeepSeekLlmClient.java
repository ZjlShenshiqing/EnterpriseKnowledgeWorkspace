package com.zjl.knowledge.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.model.ChatMessage;
import com.zjl.knowledge.agent.model.ChatUsage;
import com.zjl.knowledge.agent.model.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 客户端实现（OpenAI 兼容格式）
 *
 * <p>使用 Java 原生 HTTPURLConnection 实现流式对话和工具调用。
 * DeepSeek API 采用 OpenAI 兼容的接口格式，支持 SSE（Server-Sent Events）流式响应。</p>
 *
 * <p>当配置 `app.agent.llm.provider=deepseek` 时自动启用此客户端。</p>
 *
 * <p>配置要求：</p>
 * <ul>
 *   <li>app.agent.llm.provider=deepseek</li>
 *   <li>app.agent.llm.api-key=your-api-key</li>
 *   <li>app.agent.llm.base-url=https://api.deepseek.com</li>
 *   <li>app.agent.llm.model=deepseek-chat</li>
 * </ul>
 *
 * <p>核心特性：</p>
 * <ul>
 *   <li>SSE 流式响应，实时返回文本片段</li>
 *   <li>支持工具调用（Tool Calling）</li>
 *   <li>OpenAI 兼容的请求/响应格式</li>
 *   <li>30秒连接超时，120秒读取超时</li>
 * </ul>
 *
 * @see LlmClient
 * @see AgentProperties
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "app.agent.llm.provider", havingValue = "deepseek")
public class DeepSeekLlmClient implements LlmClient {

    /**
     * Agent 配置属性，包含 LLM 相关配置
     */
    private final AgentProperties agentProperties;

    /**
     * JSON 序列化/反序列化工具
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     *
     * @param agentProperties Agent 配置属性
     */
    public DeepSeekLlmClient(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 流式对话接口
     *
     * <p>调用 DeepSeek API 进行流式响应，支持工具调用能力。
     * 使用 SSE（Server-Sent Events）协议实时接收响应。</p>
     *
     * @param messages 对话消息列表，包含用户、助手、工具调用等角色的消息
     * @param tools 可用工具定义列表，模型将根据上下文决定是否调用工具
     * @param listener 流式响应监听器，用于接收文本片段、工具调用和结束信号
     */
    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener) {
        AgentProperties.Llm config = agentProperties.getLlm();

        try {
            // 构建 OpenAI 兼容格式的请求体
            Map<String, Object> requestBody = buildRequestBody(messages, tools, config);
            String json = objectMapper.writeValueAsString(requestBody);

            // 构建完整的 API URL
            String apiUrl = config.getBaseUrl().replaceAll("/$", "") + "/v1/chat/completions";
            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();

            // 配置 HTTP 请求
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);  // 30秒连接超时
            conn.setReadTimeout(120_000);    // 120秒读取超时

            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            // 检查响应状态码
            int status = conn.getResponseCode();
            if (status != 200) {
                // 读取错误响应体并通知监听器
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String body = reader.lines().reduce("", (a, b) -> a + b);
                    listener.onError(new RuntimeException("DeepSeek API error " + status + ": " + body));
                }
                return;
            }

            // 解析流式响应
            parseStreamResponse(conn, listener);

        } catch (Exception e) {
            log.error("DeepSeek LLM 调用失败", e);
            listener.onError(e);
        }
    }

    /**
     * 构建 OpenAI 兼容格式的请求体
     *
     * @param messages 对话消息列表
     * @param tools 工具定义列表
     * @param config LLM 配置
     * @return 构建好的请求体 Map
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools,
                                                  AgentProperties.Llm config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens());

        // 转换消息格式为 OpenAI 兼容格式
        List<Map<String, Object>> openaiMessages = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            
            // 工具响应消息需要包含 tool_call_id
            if ("tool".equals(msg.getRole())) {
                m.put("tool_call_id", msg.getToolCallId());
                m.put("content", msg.getContent());
            } else if (msg.getContent() != null) {
                m.put("content", msg.getContent());
            }
            
            // 处理工具调用
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", tc.getId());
                    c.put("type", "function");
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", tc.getName());
                    f.put("arguments", objectMapper.valueToTree(tc.getArguments()));
                    c.put("function", f);
                    calls.add(c);
                }
                m.put("tool_calls", calls);
            }
            openaiMessages.add(m);
        }
        body.put("messages", openaiMessages);

        // 转换工具定义为 OpenAI 兼容格式
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> openaiTools = new ArrayList<>();
            for (ToolDefinition td : tools) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("type", "function");
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("name", td.getName());
                f.put("description", td.getDescription());
                if (td.getInputSchema() != null) {
                    f.put("parameters", buildOpenAiParameters(td.getInputSchema()));
                }
                t.put("function", f);
                openaiTools.add(t);
            }
            body.put("tools", openaiTools);
        }

        return body;
    }

    /**
     * 将工具输入 Schema 转换为 OpenAI 兼容的参数格式
     *
     * @param schema 工具输入 Schema 定义
     * @return OpenAI 兼容的参数定义 Map
     */
    private Map<String, Object> buildOpenAiParameters(ToolDefinition.JsonSchema schema) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", schema.getType());
        
        // 添加必填字段列表
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            params.put("required", schema.getRequired());
        }
        
        // 添加属性定义
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, Object> props = new LinkedHashMap<>();
            for (Map.Entry<String, ToolDefinition.PropertyDef> entry : schema.getProperties().entrySet()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                ToolDefinition.PropertyDef def = entry.getValue();
                prop.put("type", def.getType());
                if (def.getDescription() != null) {
                    prop.put("description", def.getDescription());
                }
                if (def.getEnumValues() != null) {
                    prop.put("enum", def.getEnumValues());
                }
                props.put(entry.getKey(), prop);
            }
            params.put("properties", props);
        }
        return params;
    }

    /**
     * 解析 SSE 流式响应
     *
     * <p>逐行读取 HTTP 响应流，解析 OpenAI 格式的 SSE 数据，提取文本片段和工具调用信息。</p>
     *
     * @param conn HTTP 连接
     * @param listener 流式响应监听器
     * @throws Exception 解析过程中的异常
     */
    private void parseStreamResponse(HttpURLConnection conn, StreamListener listener) throws Exception {
        StringBuilder fullContent = new StringBuilder();  // 累计完整响应内容
        List<ToolCall> toolCalls = new ArrayList<>();      // 收集所有工具调用
        Map<Integer, ToolCall> toolCallMap = new LinkedHashMap<>();  // 按索引缓存工具调用

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // SSE 格式：data: {json}
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    
                    // 结束标记
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    
                    try {
                        // 解析 JSON 数据
                        @SuppressWarnings("unchecked")
                        Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                        if (choices == null || choices.isEmpty()) {
                            continue;
                        }
                        
                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta == null) {
                            continue;
                        }

                        // 处理文本内容
                        if (delta.containsKey("content") && delta.get("content") != null) {
                            String text = (String) delta.get("content");
                            fullContent.append(text);
                            listener.onTextDelta(text);  // 通知监听器收到文本片段
                        }

                        // 处理工具调用
                        if (delta.containsKey("tool_calls")) {
                            List<Map<String, Object>> tcList = (List<Map<String, Object>>) delta.get("tool_calls");
                            for (Map<String, Object> tc : tcList) {
                                int index = ((Number) tc.get("index")).intValue();
                                // 获取或创建工具调用对象
                                ToolCall call = toolCallMap.computeIfAbsent(index, k -> {
                                    String id = tc.containsKey("id") ? (String) tc.get("id") : "";
                                    ToolCall newCall = ToolCall.builder().id(id).build();
                                    toolCalls.add(newCall);
                                    listener.onToolCall(newCall);  // 通知监听器收到工具调用
                                    return newCall;
                                });

                                // 更新工具调用详情
                                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                                if (func != null) {
                                    if (func.containsKey("name") && func.get("name") != null) {
                                        call.setName((String) func.get("name"));
                                    }
                                    if (func.containsKey("arguments")) {
                                        String args = (String) func.get("arguments");
                                        if (args != null && !args.isEmpty()) {
                                            try {
                                                Map<String, Object> parsed = objectMapper.readValue(args, Map.class);
                                                call.setArguments(parsed);
                                            } catch (JsonProcessingException e) {
                                                log.warn("工具调用参数解析失败: {}", args);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略解析失败的 chunk，继续处理后续数据
                        log.debug("SSE chunk 解析跳过: {}", data.substring(0, Math.min(100, data.length())));
                    }
                }
            }
        }

        // 通知监听器响应完成
        listener.onDone(ChatUsage.builder()
                .inputTokens(0)
                .outputTokens(fullContent.length() / 4)  // 粗略估算 token 数量
                .build());
    }
}
