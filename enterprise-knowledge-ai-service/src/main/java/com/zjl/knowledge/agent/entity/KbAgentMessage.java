package com.zjl.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 对话消息实体
 *
 * <p>存储 Agent 对话过程中的消息记录，包括用户消息、Agent 回复以及工具调用信息。
 * 支持多轮对话场景，通过 sessionId 关联同一会话的所有消息。</p>
 *
 * <p>典型对话流程：</p>
 * <ul>
 *   <li>用户消息：role=user, content=用户输入</li>
 *   <li>Agent 回复：role=assistant, content=AI 响应</li>
 *   <li>工具调用：role=tool, toolName=工具名, toolInput=输入, toolOutput=输出</li>
 * </ul>
 *
 * @see <a href="https://github.com/alliancebio/Moss">Moss Agent</a>
 */
@Data
@TableName("kb_agent_message")
public class KbAgentMessage {

    /**
     * 消息唯一标识
     *
     * <p>使用雪花算法自动生成，保证分布式环境下 ID 的唯一性和趋势递增性。</p>
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话 ID
     *
     * <p>用于关联同一轮对话的所有消息。同一 sessionId 下的消息属于同一个对话上下文。</p>
     */
    private Long sessionId;

    /**
     * 消息角色
     *
     * <p>取值范围：user（用户）、assistant（AI助手）、tool（工具调用结果）</p>
     */
    private String role;

    /**
     * 消息内容
     *
     * <p>存储文本内容。user 和 assistant 角色时为对话文本，tool 角色时通常为空。</p>
     */
    private String content;

    /**
     * 调用的工具名称
     *
     * <p>仅在 role=tool 时有值，表示本次消息关联的工具调用操作。</p>
     */
    private String toolName;

    /**
     * 工具输入参数
     *
     * <p>工具调用时的输入参数，通常为 JSON 格式字符串。</p>
     */
    private String toolInput;

    /**
     * 工具输出结果
     *
     * <p>工具执行后的返回结果，通常为 JSON 格式字符串。</p>
     */
    private String toolOutput;

    /**
     * Token 数量
     *
     * <p>本次消息消耗的 token 数量，用于用量统计和成本控制。</p>
     */
    private Integer tokenCount;

    /**
     * 消息创建时间
     *
     * <p>精确到秒，记录消息生成的时间戳。</p>
     */
    private LocalDateTime createdAt;
}
