package com.zjl.knowledge.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.model.ChatMessage;
import com.zjl.knowledge.agent.model.ChatUsage;
import com.zjl.knowledge.agent.model.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Spring AI 的 LLM 客户端实现
 *
 * <p>使用 Spring AI 的 {@link OpenAiApi} 对接 DeepSeek（OpenAI 兼容格式），
 * 替代原有的手动 HttpURLConnection + SSE 解析方式。</p>
 *
 * <p>当配置 {@code app.agent.llm.provider=deepseek} 时自动启用。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>使用底层 OpenAiApi 而非高层 ChatClient，因为 AgentLoop 自行控制工具执行流程</li>
 *   <li>OpenAiApi 负责 HTTP 通信、认证、JSON 序列化、SSE 反序列化</li>
 *   <li>通过 CountDownLatch 将响应式 Flux 桥接到同步回调接口 StreamListener</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "app.agent.llm.provider", havingValue = "deepseek")
public class SpringAiLlmClient implements LlmClient {

    private final AgentProperties agentProperties;
    private final OpenAiApi openAiApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造 Spring AI LLM 客户端
     *
     * @param agentProperties Agent 配置属性
     */
    public SpringAiLlmClient(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
        AgentProperties.Llm config = agentProperties.getLlm();

        String baseUrl = normalizeOpenAiBaseUrl(config.getBaseUrl());
        String apiKey = resolveApiKey(config);

        if (apiKey.isEmpty()) {
            log.warn("DeepSeek API Key 未配置：请在 application-secrets.yml 设置 app.agent.llm.api-key，"
                    + "或设置环境变量 DEEPSEEK_API_KEY。智能对话将无法调用 LLM。");
        }

        this.openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        log.info("SpringAiLlmClient 初始化完成, baseUrl={}, model={}, apiKey={}",
                baseUrl, config.getModel(),
                apiKey.isEmpty() ? "<empty>" : apiKey.substring(0, Math.min(8, apiKey.length())) + "***");
    }

    /**
     * Spring AI OpenAiApi 会自动追加 {@code /v1/chat/completions}，base-url 只需主机根路径。
     */
    private static String normalizeOpenAiBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "https://api.deepseek.com";
        }
        String url = raw.trim().replaceAll("/+$", "");
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    /**
     * 解析 DeepSeek API Key：优先配置文件，其次环境变量。
     */
    private static String resolveApiKey(AgentProperties.Llm config) {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey().trim();
        }
        String envKey = System.getenv("DEEPSEEK_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return "";
    }

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener) {
        AgentProperties.Llm config = agentProperties.getLlm();

        try {
            String apiKey = resolveApiKey(config);
            if (apiKey.isEmpty()) {
                listener.onError(new IllegalStateException("DeepSeek API Key 未配置，请检查 application-secrets.yml 或 DEEPSEEK_API_KEY"));
                return;
            }

            OpenAiApi.ChatCompletionRequest request = buildRequest(messages, tools, config);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ChatUsage> usageRef = new AtomicReference<>();
            AtomicBoolean errorOccurred = new AtomicBoolean(false);
            Map<Integer, ToolCall> toolCallMap = new LinkedHashMap<>();
            Map<Integer, StringBuilder> toolArgBufferMap = new HashMap<>();
            Set<Integer> notifiedToolCalls = new HashSet<>();

            openAiApi.chatCompletionStream(request)
                    .subscribe(
                            chunk -> processChunk(chunk, listener, toolCallMap, toolArgBufferMap, notifiedToolCalls),
                            error -> {
                                if (error instanceof WebClientResponseException wcre) {
                                    log.error("Spring AI 流式调用失败: status={}, body={}",
                                            wcre.getStatusCode(), wcre.getResponseBodyAsString());
                                } else {
                                    log.error("Spring AI 流式调用失败", error);
                                }
                                errorOccurred.set(true);
                                listener.onError(error);
                                latch.countDown();
                            },
                            () -> {
                                if (!errorOccurred.get()) {
                                    ChatUsage usage = usageRef.get();
                                    if (usage == null) {
                                        usage = ChatUsage.builder()
                                                .inputTokens(0)
                                                .outputTokens(0)
                                                .build();
                                    }
                                    listener.onDone(usage);
                                }
                                latch.countDown();
                            }
                    );

            boolean completed = latch.await(130, TimeUnit.SECONDS);
            if (!completed) {
                listener.onError(new RuntimeException("LLM 流式响应超时（130秒）"));
            }
        } catch (Exception e) {
            log.error("SpringAiLlmClient 调用失败", e);
            listener.onError(e);
        }
    }

    /**
     * 构建 OpenAI 兼容格式的请求
     */
    private OpenAiApi.ChatCompletionRequest buildRequest(
            List<ChatMessage> messages, List<ToolDefinition> tools, AgentProperties.Llm config) {

        List<OpenAiApi.ChatCompletionMessage> apiMessages = convertMessages(messages);
        List<OpenAiApi.FunctionTool> apiTools = convertTools(tools);

        return new OpenAiApi.ChatCompletionRequest(
                apiMessages,
                config.getModel(),
                null,
                null,
                null,
                null,
                null,
                null,
                config.getMaxTokens(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                config.getTemperature(),
                null,
                apiTools.isEmpty() ? null : apiTools,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 将项目 ChatMessage 转换为 Spring AI ChatCompletionMessage
     */
    private List<OpenAiApi.ChatCompletionMessage> convertMessages(List<ChatMessage> messages) {
        List<OpenAiApi.ChatCompletionMessage> result = new ArrayList<>(messages.size());
        for (ChatMessage msg : messages) {
            String role = msg.getRole();
            String content = msg.getContent() != null ? msg.getContent() : "";

            switch (role) {
                case "system" -> result.add(
                        new OpenAiApi.ChatCompletionMessage(content,
                                OpenAiApi.ChatCompletionMessage.Role.SYSTEM));

                case "user" -> result.add(
                        new OpenAiApi.ChatCompletionMessage(content,
                                OpenAiApi.ChatCompletionMessage.Role.USER));

                case "assistant" -> {
                    List<OpenAiApi.ChatCompletionMessage.ToolCall> toolCalls = null;
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        toolCalls = new ArrayList<>();
                        for (ToolCall tc : msg.getToolCalls()) {
                            if (tc.getName() == null || tc.getName().isBlank()) {
                                log.warn("跳过缺少名称的 assistant tool_call: id={}", tc.getId());
                                continue;
                            }
                            String argsJson = "{}";
                            try {
                                argsJson = objectMapper.writeValueAsString(
                                        tc.getArguments() != null ? tc.getArguments() : Map.of());
                            } catch (JsonProcessingException e) {
                                log.warn("工具调用参数序列化失败: {}", tc.getName());
                            }
                            toolCalls.add(new OpenAiApi.ChatCompletionMessage.ToolCall(
                                    resolveToolCallId(tc),
                                    "function",
                                    new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction(
                                            tc.getName(), argsJson)));
                        }
                        if (toolCalls.isEmpty()) {
                            toolCalls = null;
                        }
                    }
                    String assistantContent = content;
                    if (toolCalls != null && assistantContent.isEmpty()) {
                        assistantContent = null;
                    }
                    result.add(new OpenAiApi.ChatCompletionMessage(
                            assistantContent,
                            OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                            null,
                            null,
                            toolCalls,
                            null,
                            null,
                            null));
                }

                case "tool" -> {
                    if (msg.getToolCallId() == null || msg.getToolCallId().isBlank()) {
                        log.warn("跳过缺少 tool_call_id 的 tool 消息，避免 LLM 请求 400");
                        continue;
                    }
                    // 参数顺序：content, role, name, toolCallId, toolCalls, refusal, audio, annotations
                    result.add(
                            new OpenAiApi.ChatCompletionMessage(
                                    content,
                                    OpenAiApi.ChatCompletionMessage.Role.TOOL,
                                    null,
                                    msg.getToolCallId(),
                                    null,
                                    null,
                                    null,
                                    null));
                }

                default -> {
                    log.warn("未知的消息角色: {}, 按 user 角色处理", role);
                    result.add(new OpenAiApi.ChatCompletionMessage(content,
                            OpenAiApi.ChatCompletionMessage.Role.USER));
                }
            }
        }
        return result;
    }

    /**
     * 将 ToolDefinition 转换为 Spring AI FunctionTool
     *
     * <p>只传工具定义不做回调注册，LLM 返回的工具调用由 AgentLoop 自行执行。</p>
     */
    private List<OpenAiApi.FunctionTool> convertTools(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }

        List<OpenAiApi.FunctionTool> result = new ArrayList<>(tools.size());
        for (ToolDefinition td : tools) {
            Map<String, Object> parameters = buildParameters(td.getInputSchema());
            result.add(new OpenAiApi.FunctionTool(
                    OpenAiApi.FunctionTool.Type.FUNCTION,
                    new OpenAiApi.FunctionTool.Function(
                            td.getDescription(), td.getName(), parameters, null)));
        }
        return result;
    }

    /**
     * 将 ToolDefinition.JsonSchema 转换为 OpenAI 兼容的 parameters Map
     */
    private Map<String, Object> buildParameters(ToolDefinition.JsonSchema schema) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", schema.getType() != null ? schema.getType() : "object");

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
        } else {
            params.put("properties", Map.of());
        }

        return params;
    }

    /**
     * 处理单个 SSE Chunk
     *
     * <p>从 chunk 中提取文本 delta 和工具调用信息，通过 StreamListener 回调通知。</p>
     *
     * @param chunk SSE 响应块
     * @param listener 流式响应监听器
     * @param toolCallMap 按 index 缓存的工具调用（用于跨 chunk 聚合）
     * @param toolArgBufferMap 按 index 缓存的工具参数字符串（用于跨 chunk 拼接）
     */
    private void processChunk(OpenAiApi.ChatCompletionChunk chunk,
                               StreamListener listener,
                               Map<Integer, ToolCall> toolCallMap,
                               Map<Integer, StringBuilder> toolArgBufferMap,
                               Set<Integer> notifiedToolCalls) {
        try {
            if (chunk.choices() == null || chunk.choices().isEmpty()) {
                return;
            }

            OpenAiApi.ChatCompletionMessage delta = chunk.choices().get(0).delta();
            if (delta == null) {
                return;
            }

            // 处理文本内容
            if (delta.rawContent() instanceof String text && !text.isEmpty()) {
                listener.onTextDelta(text);
            } else if (delta.content() != null && !delta.content().isEmpty()) {
                listener.onTextDelta(delta.content());
            }

            // 处理工具调用
            if (delta.toolCalls() != null && !delta.toolCalls().isEmpty()) {
                for (OpenAiApi.ChatCompletionMessage.ToolCall deltaTc : delta.toolCalls()) {
                    int index = deltaTc.index() != null ? deltaTc.index() : 0;

                    ToolCall call = toolCallMap.computeIfAbsent(index, k -> ToolCall.builder()
                            .id(resolveToolCallId(deltaTc.id(), index))
                            .build());
                    StringBuilder argBuffer = toolArgBufferMap.computeIfAbsent(index, k -> new StringBuilder());

                    // 更新工具调用详情
                    OpenAiApi.ChatCompletionMessage.ChatCompletionFunction func = deltaTc.function();
                    if (func != null) {
                        if (func.name() != null && !func.name().isEmpty()) {
                            call.setName(func.name());
                            notifyToolCallIfReady(call, index, notifiedToolCalls, listener);
                        }
                        if (func.arguments() != null && !func.arguments().isEmpty()) {
                            argBuffer.append(func.arguments());
                            tryUpdateToolArguments(call, argBuffer);
                        }
                    }

                    // 补充 ID（某些 chunk 可能延迟携带）
                    if ((call.getId() == null || call.getId().isBlank())
                            && deltaTc.id() != null && !deltaTc.id().isBlank()) {
                        call.setId(deltaTc.id());
                    }
                    ensureToolCallId(call, index);
                }
            }
        } catch (Exception e) {
            log.debug("SSE chunk 处理跳过: {}", e.getMessage());
        }
    }

    /**
     * 工具名称解析完成后通知 AgentLoop，避免流式首包 name 为空时触发 NPE。
     */
    private void notifyToolCallIfReady(ToolCall call, int index, Set<Integer> notifiedToolCalls,
                                       StreamListener listener) {
        if (notifiedToolCalls.contains(index)) {
            return;
        }
        if (call.getName() == null || call.getName().isBlank()) {
            return;
        }
        ensureToolCallId(call, index);
        notifiedToolCalls.add(index);
        listener.onToolCall(call);
    }

    private static void ensureToolCallId(ToolCall call, int index) {
        if (call.getId() == null || call.getId().isBlank()) {
            call.setId("call_" + index);
        }
    }

    private static String resolveToolCallId(ToolCall tc) {
        if (tc.getId() != null && !tc.getId().isBlank()) {
            return tc.getId();
        }
        return "call_" + tc.getName();
    }

    private static String resolveToolCallId(String id, int index) {
        if (id != null && !id.isBlank()) {
            return id;
        }
        return "call_" + index;
    }

    /**
     * 尝试把已累计的参数字符串解析为 JSON。
     *
     * <p>DeepSeek/OpenAI 兼容流式工具调用经常会把 arguments 拆成多个 chunk 返回，
     * 因此这里需要先累计，再在形成完整 JSON 后一次性解析。</p>
     */
    private void tryUpdateToolArguments(ToolCall call, StringBuilder argBuffer) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(argBuffer.toString(), Map.class);
            call.setArguments(new LinkedHashMap<>(parsed));
        } catch (JsonProcessingException e) {
            log.debug("工具调用参数尚未形成完整 JSON，继续等待后续 chunk");
        }
    }
}
