package com.zjl.knowledge.agent.llm;

import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.model.ChatMessage;

import java.util.List;

/**
 * LLM 客户端抽象，支持流式对话与 tool calling
 */
public interface LlmClient {

    /**
     * 流式对话（带 tools）
     *
     * @param messages  对话历史（含 system prompt）
     * @param tools     可用工具定义
     * @param listener  流式回调
     */
    void chatStream(List<ChatMessage> messages, List<ToolDefinition> tools, StreamListener listener);
}
