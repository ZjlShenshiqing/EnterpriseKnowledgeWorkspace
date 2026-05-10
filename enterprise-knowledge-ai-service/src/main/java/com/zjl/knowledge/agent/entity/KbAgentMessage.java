package com.zjl.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 对话消息实体。
 */
@Data
@TableName("kb_agent_message")
public class KbAgentMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String toolName;

    private String toolInput;

    private String toolOutput;

    private Integer tokenCount;

    private LocalDateTime createdAt;
}
