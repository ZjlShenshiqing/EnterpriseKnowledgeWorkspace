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
 * Anthropic Messages API 客户端实现。
 *
 * <p>使用 Anthropic Java SDK 进行流式对话和 tool calling。
 * 当 app.agent.llm.provider=anthropic 时启用</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.agent.llm.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicLlmClient implements LlmClient {

    private final AgentProperties agentProperties;

    @Override
    public void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener) {
        AgentProperties.Llm config = agentProperties.getLlm();

        try {
            // TODO: 接入 Anthropic SDK
            // AnthropicClient client = AnthropicClient.builder()
            //         .apiKey(config.getApiKey())
            //         .baseUrl(config.getBaseUrl())
            //         .build();
            //
            // client.messages().createStream(...)

            log.info("AnthropicLlmClient invoked: model={}, messages={}, tools={}",
                    config.getModel(), messages.size(), tools.size());

            listener.onTextDelta("LLM 客户端尚未接入真实 API。请配置 app.agent.llm.api-key 后重试。");
            listener.onDone(ChatUsage.builder().inputTokens(0).outputTokens(0).build());

        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            listener.onError(e);
        }
    }
}
