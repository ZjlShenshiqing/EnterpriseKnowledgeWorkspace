package com.zjl.knowledge.agent.llm;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
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
        throw new BizException(ErrorCode.SYSTEM_ERROR,
                "Anthropic provider 尚未实现，请使用其他 LLM provider（如 dashscope）");
    }
}
