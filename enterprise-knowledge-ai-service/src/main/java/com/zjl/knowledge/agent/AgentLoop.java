package com.zjl.knowledge.agent;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.agent.entity.KbAgentSession;
import com.zjl.knowledge.agent.llm.LlmClient;
import com.zjl.knowledge.agent.llm.StreamListener;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolRegistry;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.agent.model.ChatMessage;
import com.zjl.knowledge.agent.model.ChatUsage;
import com.zjl.knowledge.agent.model.ToolCall;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 循环：用户输入 → LLM → tool call → 执行 → 回填 → 循环直到完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private static final int MAX_TURNS = 10;

    private static final String SYSTEM_PROMPT = """
            你是企业知识库助手。你的职责是帮助员工查找和了解公司内部文档。

            可用工具：search_documents、list_documents、get_document_detail、list_knowledge_bases

            规则：
            1. 只回答与知识库文档相关的问题
            2. 当用户询问文档内容时，先搜索再回答
            3. 回答时引用具体的文档标题和来源
            4. 不要编造信息，找不到就说找不到
            5. 不讨论文档上传、删除、分块等管理操作
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AgentSessionService sessionService;
    private final AgentProperties agentProperties;

    /**
     * 执行 Agent 对话循环。
     *
     * @param session   会话
     * @param user      当前用户
     * @param emitter   SSE 事件发射器
     */
    public void run(KbAgentSession session, UserContext user, SseEmitter emitter) {
        try {
            agentLoop(session, user, emitter);
        } catch (Exception e) {
            log.error("Agent 循环异常, sessionId={}", session.getId(), e);
            emitter.error(e.getMessage());
        }
    }

    private void agentLoop(KbAgentSession session, UserContext user, SseEmitter emitter) {
        // ① 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build());

        // 加载历史消息
        List<ChatMessage> history = sessionService.loadHistory(
                session.getId(), agentProperties.getSession().getMaxHistory());
        messages.addAll(history);

        // ② 获取 tools
        List<ToolDefinition> tools = toolRegistry.getAllDefinitions();

        // ③ LLM 循环
        int turn = 0;
        StringBuilder fullResponse = new StringBuilder();

        while (turn < MAX_TURNS) {
            turn++;
            final int currentTurn = turn;
            final StringBuilder turnText = new StringBuilder();

            llmClient.chatStream(messages, tools, new StreamListener() {
                @Override
                public void onTextDelta(String delta) {
                    turnText.append(delta);
                    emitter.send(SseEmitter.event("message",
                            Map.of("delta", delta, "type", "text")));
                }

                @Override
                public void onToolCall(ToolCall call) {
                    emitter.send(SseEmitter.event("tool_call",
                            Map.of("tool", call.getName(), "args", call.getArguments())));
                }

                @Override
                public void onDone(ChatUsage usage) {
                    fullResponse.append(turnText);
                    emitter.send(SseEmitter.event("done",
                            Map.of("sessionId", session.getId(),
                                    "tokenUsage", Map.of(
                                            "input", usage.getInputTokens(),
                                            "output", usage.getOutputTokens()))));
                }

                @Override
                public void onError(Throwable error) {
                    emitter.error("LLM 调用失败: " + error.getMessage());
                }
            });

            // 如果有 tool call，需要手动处理
            // （当前占位实现不返回 tool call，真实实现需解析 LLM 响应）
            if (currentTurn >= MAX_TURNS) {
                emitter.send(SseEmitter.event("message",
                        Map.of("delta", "\n\n已达到最大对话轮次，请重新提问。",
                                "type", "text")));
            }
            break; // 占位实现只执行一轮
        }

        // ④ 保存消息
        try {
            sessionService.saveAssistantMessage(session.getId(),
                    fullResponse.toString(), null);
        } catch (Exception e) {
            log.warn("保存助手消息失败", e);
        }

        emitter.complete();
    }
}
