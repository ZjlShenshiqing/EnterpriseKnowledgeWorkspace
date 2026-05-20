package com.zjl.knowledge.agent.llm;

import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.model.ChatMessage;
import com.zjl.knowledge.agent.model.ChatUsage;
import com.zjl.knowledge.agent.model.ToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 客户端实现
 *
 * <p>使用 Anthropic Java SDK 进行流式对话和工具调用（Tool Calling）。
 * 当配置 `app.agent.llm.provider=anthropic` 时自动启用此客户端。</p>
 *
 * <p>支持的模型：Claude 3 Opus、Claude 3 Sonnet、Claude 3 Haiku</p>
 *
 * <p>配置要求：</p>
 * <ul>
 *   <li>app.agent.llm.provider=anthropic</li>
 *   <li>app.agent.llm.api-key=your-api-key</li>
 *   <li>app.agent.llm.model=claude-3-sonnet-20240229（或其他模型）</li>
 * </ul>
 *
 * @see LlmClient
 * @see AgentProperties
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.agent.llm.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    /**
     * Agent 配置属性
     */
    private final AgentProperties agentProperties;

    /**
     * 流式对话接口
     *
     * <p>调用 Anthropic Messages API 进行流式响应，支持工具调用能力。</p>
     *
     * @param messages 对话消息列表，包含用户、助手、工具调用等角色的消息
     * @param tools 可用工具定义列表，Anthropic 将根据上下文决定是否调用工具
     * @param listener 流式响应监听器，用于接收文本片段、工具调用和结束信号
     */
    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener) {
        AgentProperties.Llm config = agentProperties.getLlm();

        try {
            // TODO: 接入 Anthropic Java SDK
            // 参考代码结构：
            // AnthropicClient client = AnthropicClient.builder()
            //         .apiKey(config.getApiKey())
            //         .baseUrl(config.getBaseUrl())
            //         .build();
            //
            // client.messages()
            //         .createStream(MessageCreateStreamRequest.builder()
            //                 .model(config.getModel())
            //                 .messages(convertToAnthropicMessages(messages))
            //                 .tools(convertToAnthropicTools(tools))
            //                 .stream(true)
            //                 .build())
            //         .subscribe(response -> {
            //             // 处理流式响应
            //             listener.onTextDelta(response.delta().text());
            //         });

            // 记录调用日志
            log.info("AnthropicLlmClient invoked: model={}, messages={}, tools={}",
                    config.getModel(), messages.size(), tools.size());

            // 当前为占位实现，返回提示信息
            listener.onTextDelta("LLM 客户端尚未接入真实 API。请配置 app.agent.llm.api-key 后重试。");
            listener.onDone(ChatUsage.builder().inputTokens(0).outputTokens(0).build());

        } catch (Exception e) {
            // 记录错误日志并通知监听器
            log.error("Anthropic LLM 调用失败", e);
            listener.onError(e);
        }
    }
}
