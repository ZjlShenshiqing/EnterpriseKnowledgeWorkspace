package com.zjl.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 对话会话实体
 *
 * <p>存储 Agent 对话会话的元信息，包括会话标题、状态、创建和更新时间。
 * 会话是用户与 Agent 交互的基本单元，一个会话可包含多条消息（通过 sessionId 关联）。</p>
 *
 * <p>生命周期：</p>
 * <ul>
 *   <li>创建会话 → status=ACTIVE</li>
 *   <li>用户继续对话 → 追加消息记录</li>
 *   <li>结束会话 → status=CLOSED</li>
 * </ul>
 *
 * @see KbAgentMessage
 */
@Data
@TableName("kb_agent_session")
public class KbAgentSession {

    /**
     * 会话唯一标识
     *
     * <p>使用雪花算法自动生成，用于关联该会话下的所有消息记录。</p>
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户 ID
     *
     * <p>会话所属用户的标识，用于多用户隔离和会话归属查询。</p>
     */
    private Long userId;

    /**
     * 会话标题
     *
     * <p>由 AI 自动生成或用户手动设置的会话名称，通常取自第一轮对话的内容摘要。
     * 用于在会话列表中快速识别会话主题。</p>
     */
    private String title;

    /**
     * 会话状态
     *
     * <p>取值范围：ACTIVE（进行中）、CLOSED（已结束）</p>
     */
    private String status;

    /**
     * 会话创建时间
     *
     * <p>记录会话创建的时间戳，创建后不可变更。</p>
     */
    private LocalDateTime createdAt;

    /**
     * 会话更新时间
     *
     * <p>记录最后一次新消息或状态变更的时间，用于会话列表排序。</p>
     */
    private LocalDateTime updatedAt;
}
