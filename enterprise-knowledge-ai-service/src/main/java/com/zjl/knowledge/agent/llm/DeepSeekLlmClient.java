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
 * DeepSeek API 客户端实现（OpenAI 兼容格式，支持 tool calling + SSE 流式）。
 *
 * <p>当 app.agent.llm.provider=deepseek 时启用</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "app.agent.llm.provider", havingValue = "deepseek")
public class DeepSeekLlmClient implements LlmClient {

    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekLlmClient(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener) {
        AgentProperties.Llm config = agentProperties.getLlm();

        try {
            Map<String, Object> requestBody = buildRequestBody(messages, tools, config);
            String json = objectMapper.writeValueAsString(requestBody);

            String apiUrl = config.getBaseUrl().replaceAll("/$", "") + "/v1/chat/completions";
            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(120_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String body = reader.lines().reduce("", (a, b) -> a + b);
                    listener.onError(new RuntimeException("DeepSeek API error " + status + ": " + body));
                }
                return;
            }

            parseStreamResponse(conn, listener);

        } catch (Exception e) {
            log.error("DeepSeek LLM 调用失败", e);
            listener.onError(e);
        }
    }

    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools,
                                                  AgentProperties.Llm config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens());

        List<Map<String, Object>> openaiMessages = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            if ("tool".equals(msg.getRole())) {
                m.put("tool_call_id", msg.getToolCallId());
                m.put("content", msg.getContent());
            } else if (msg.getContent() != null) {
                m.put("content", msg.getContent());
            }
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

    private Map<String, Object> buildOpenAiParameters(ToolDefinition.JsonSchema schema) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", schema.getType());
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            params.put("required", schema.getRequired());
        }
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

    private void parseStreamResponse(HttpURLConnection conn, StreamListener listener) throws Exception {
        StringBuilder fullContent = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        Map<Integer, ToolCall> toolCallMap = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
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

                        if (delta.containsKey("content") && delta.get("content") != null) {
                            String text = (String) delta.get("content");
                            fullContent.append(text);
                            listener.onTextDelta(text);
                        }

                        if (delta.containsKey("tool_calls")) {
                            List<Map<String, Object>> tcList = (List<Map<String, Object>>) delta.get("tool_calls");
                            for (Map<String, Object> tc : tcList) {
                                int index = ((Number) tc.get("index")).intValue();
                                ToolCall call = toolCallMap.computeIfAbsent(index, k -> {
                                    String id = tc.containsKey("id") ? (String) tc.get("id") : "";
                                    ToolCall newCall = ToolCall.builder().id(id).build();
                                    toolCalls.add(newCall);
                                    listener.onToolCall(newCall);
                                    return newCall;
                                });

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
                                                log.warn("tool arguments parse error: {}", args);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("SSE chunk parse skip: {}", data.substring(0, Math.min(100, data.length())));
                    }
                }
            }
        }

        listener.onDone(ChatUsage.builder()
                .inputTokens(0)
                .outputTokens(fullContent.length() / 4)
                .build());
    }
}
