package com.zjl.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 对话会话实体
 */
@Data
@TableName("kb_agent_session")
public class KbAgentSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String title;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
