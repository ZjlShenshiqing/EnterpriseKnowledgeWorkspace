package com.zjl.knowledge.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Agent 核心循环组件
 *
 * <p>实现 Agent 对话的核心逻辑：用户输入 → LLM 调用 → 工具调用 → 结果回填 → 循环直到完成。</p>
 *
 * <p>核心工作流程：</p>
 * <ol>
 *   <li>构建消息列表（系统提示 + 历史消息）</li>
 *   <li>获取可用工具列表</li>
 *   <li>调用 LLM 生成响应</li>
 *   <li>如果 LLM 返回工具调用，则执行工具并将结果回填到消息列表</li>
 *   <li>循环执行，直到 LLM 直接回答或达到最大轮次</li>
 * </ol>
 *
 * <p>最大循环轮次：{@value #MAX_TURNS}</p>
 *
 * @see LlmClient
 * @see ToolRegistry
 * @see AgentSessionService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    /**
     * 最大对话轮次限制，防止无限循环
     */
    private static final int MAX_TURNS = 10;

    /**
     * 系统提示词（基础版，不包含联网搜索）
     */
    private static final String SYSTEM_PROMPT_BASE = """
            你是企业知识库助手。你的职责是帮助员工查找和了解公司内部文档。

            可用工具：search_documents、list_documents、get_document_detail、list_knowledge_bases

            规则：
            1. 只回答与知识库文档相关的问题
            2. 当用户询问文档内容时，先搜索再回答
            3. 回答时引用具体的文档标题和来源
            4. 不要编造信息，找不到就说找不到
            5. 不讨论文档上传、删除、分块等管理操作
            """;

    /**
     * 系统提示词（联网搜索版，额外包含 web_search 工具）
     */
    private static final String SYSTEM_PROMPT_WITH_SEARCH = """
            你是企业知识库助手。你的职责是帮助员工查找和了解公司内部文档。

            可用工具：search_documents、list_documents、get_document_detail、list_knowledge_bases、web_search

            规则：
            1. 优先使用知识库工具回答与公司文档相关的问题
            2. 当用户询问最新资讯、外部信息或知识库中找不到的内容时，使用 web_search 搜索互联网
            3. 回答时引用具体的文档标题和来源（知识库）或网页来源（联网搜索）
            4. 不要编造信息，找不到就说找不到
            5. 不讨论文档上传、删除、分块等管理操作
            """;

    /**
     * LLM 客户端，用于调用大语言模型
     */
    private final LlmClient llmClient;

    /**
     * 工具注册中心，管理所有可用的工具
     */
    private final ToolRegistry toolRegistry;

    /**
     * 会话服务，管理会话和消息存储
     */
    private final AgentSessionService sessionService;

    /**
     * Agent 配置属性
     */
    private final AgentProperties agentProperties;

    /**
     * 执行 Agent 对话循环（入口方法）
     *
     * <p>包装 agentLoop 方法，捕获所有异常并发送错误消息给客户端。</p>
     *
     * @param session   会话实体
     * @param user      当前用户上下文
     * @param emitter   SSE 事件发射器，用于向客户端推送消息
     * @param webSearchEnabled 是否启用联网搜索
     */
    public void run(KbAgentSession session, UserContext user, AgentSseEmitter emitter, boolean webSearchEnabled) {
        try {
            agentLoop(session, user, emitter, webSearchEnabled);
        } catch (Exception e) {
            log.error("Agent 循环异常, sessionId={}", session.getId(), e);
            emitter.error(e.getMessage());
        }
    }

    /**
     * Agent 核心循环逻辑
     *
     * <p>实现完整的 Agent 对话循环：</p>
     * <ol>
     *   <li>构建消息列表（系统提示 + 历史消息）</li>
     *   <li>获取可用工具列表</li>
     *   <li>调用 LLM 生成响应</li>
     *   <li>如果 LLM 返回工具调用，执行工具并回填结果</li>
     *   <li>循环直到 LLM 直接回答或达到最大轮次</li>
     *   <li>保存最终响应并结束对话</li>
     * </ol>
     *
     * @param session   会话实体
     * @param user      当前用户上下文
     * @param emitter   SSE 事件发射器
     */
    private void agentLoop(KbAgentSession session, UserContext user, AgentSseEmitter emitter, boolean webSearchEnabled) {
        // ① 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();

        // 根据是否开启联网搜索选择不同的系统提示词
        String systemPrompt = webSearchEnabled ? SYSTEM_PROMPT_WITH_SEARCH : SYSTEM_PROMPT_BASE;
        messages.add(ChatMessage.builder().role("system").content(systemPrompt).build());

        // 加载历史消息（最近 N 条），用于上下文理解
        List<ChatMessage> history = sessionService.loadHistory(
                session.getId(), agentProperties.getSession().getMaxHistory());
        messages.addAll(history);

        // 当前用户消息由 AgentController 传入前已保存到 DB，不需要加到 messages 中
        // （AgentController.saveUserMessage 已在调用前执行）

        // ② 获取可用工具定义（未开启联网搜索时排除 web_search）
        List<ToolDefinition> tools = toolRegistry.getAllDefinitions().stream()
                .filter(t -> webSearchEnabled || !"web_search".equals(t.getName()))
                .toList();

        // ③ LLM 循环：调用 LLM → 检查工具调用 → 执行工具 → 回填结果
        int turn = 0;
        StringBuilder fullResponse = new StringBuilder();  // 累积最终响应
        final boolean[] llmError = {false};                 // LLM 调用是否出错

        while (turn < MAX_TURNS) {
            turn++;
            final List<ToolCall> collectedToolCalls = new ArrayList<>();  // 收集本轮的工具调用
            final StringBuilder turnText = new StringBuilder();            // 本轮的文本响应
            final boolean[] hasToolCalls = {false};                        // 是否有工具调用

            // 调用 LLM 流式接口
            llmClient.chatStream(messages, tools, new StreamListener() {
                @Override
                public void onTextDelta(String delta) {
                    // 文本片段回调：实时推送文本到客户端
                    turnText.append(delta);
                    emitter.send("message", Map.of("delta", delta, "type", "text"));
                }

                @Override
                public void onToolCall(ToolCall call) {
                    // 工具调用回调：记录工具调用并通知客户端
                    hasToolCalls[0] = true;
                    collectedToolCalls.add(call);
                    emitter.send("tool_call", Map.of("tool", call.getName(), "args", call.getArguments()));
                }

                @Override
                public void onDone(ChatUsage usage) {
                    // LLM 调用完成回调：累积文本响应
                    fullResponse.append(turnText);
                }

                @Override
                public void onError(Throwable error) {
                    // 错误回调：发送错误消息到客户端并标记错误
                    llmError[0] = true;
                    emitter.error("LLM 调用失败: " + error.getMessage());
                }
            });

            if (llmError[0]) {
                return;
            }

            // 如果没有工具调用，说明 LLM 直接回答了问题，结束循环
            if (!hasToolCalls[0]) {
                break;
            }

            // 将助手消息（包含工具调用）添加到消息列表
            ChatMessage assistantMsg = ChatMessage.builder()
                    .role("assistant")
                    .content(turnText.toString())
                    .toolCalls(collectedToolCalls)
                    .build();
            messages.add(assistantMsg);

            // 执行所有工具调用
            for (ToolCall tc : collectedToolCalls) {
                // 调用工具执行
                ToolResult result = toolRegistry.execute(tc.getName(), tc.getArguments(), user);
                
                // 发送工具执行结果到客户端
                Object toolData = result.getData() != null ? result.getData() : "";
emitter.send("tool_result", Map.of("tool", tc.getName(), "result", toolData));
                
                // 保存工具调用记录到数据库
                sessionService.saveToolMessage(session.getId(), tc.getName(), tc.getArguments(), result.getData());
                
                // 将工具执行结果作为 tool 消息添加到消息列表（供下一轮 LLM 参考）
                messages.add(ChatMessage.builder()
                        .role("tool")
                        .toolCallId(tc.getId())
                        .toolName(tc.getName())
                        .content(toJson(result.getData()))
                        .build());
            }
        }

        // 达到最大轮次限制时提示用户
        if (turn >= MAX_TURNS) {
            emitter.send("message", Map.of("delta", "\n\n已达到最大对话轮次，请重新提问。", "type", "text"));
        }

        // ④ 保存助手回复消息到数据库
        try {
            sessionService.saveAssistantMessage(session.getId(), fullResponse.toString(), null);
        } catch (Exception e) {
            log.warn("保存助手消息失败", e);
        }

        // 发送对话结束标记，完成 SSE 连接
        emitter.send("done", Map.of("sessionId", String.valueOf(session.getId())));
        emitter.complete();
    }

    /**
     * JSON 序列化器（静态单例）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将对象转换为 JSON 字符串
     *
     * <p>安全转换，转换失败时返回对象的字符串表示。</p>
     *
     * @param obj 要转换的对象
     * @return JSON 字符串或对象的字符串表示
     */
    private static String toJson(Object obj) {
        try { 
            return MAPPER.writeValueAsString(obj); 
        } catch (Exception e) { 
            return String.valueOf(obj); 
        }
    }
}
