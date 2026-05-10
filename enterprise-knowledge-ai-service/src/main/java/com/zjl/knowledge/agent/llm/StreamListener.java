package com.zjl.knowledge.agent.llm;

import com.zjl.knowledge.agent.model.ChatUsage;
import com.zjl.knowledge.agent.model.ToolCall;

/**
 * LLM 流式响应回调。
 */
public interface StreamListener {

    /**
     * 文本增量。
     *
     * @param delta 增量文本
     */
    void onTextDelta(String delta);

    /**
     * LLM 发起的 tool call。
     *
     * @param call tool call 信息
     */
    void onToolCall(ToolCall call);

    /**
     * 流式响应完成。
     *
     * @param usage token 用量
     */
    void onDone(ChatUsage usage);

    /**
     * 发生错误。
     *
     * @param error 异常
     */
    void onError(Throwable error);
}
