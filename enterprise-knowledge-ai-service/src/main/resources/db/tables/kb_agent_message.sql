-- kb_agent_message Agent对话消息表
CREATE TABLE IF NOT EXISTS kb_agent_message (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(16) NOT NULL COMMENT '消息角色 user/assistant/tool',
    content LONGTEXT NULL COMMENT '文本内容（user或assistant消息）',
    tool_name VARCHAR(128) NULL COMMENT '工具名（role=tool时）',
    tool_input JSON NULL COMMENT '工具入参JSON（role=tool时）',
    tool_output JSON NULL COMMENT '工具返回结果JSON（role=tool时）',
    token_count INT NULL COMMENT 'Token用量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_agent_message_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent对话消息表';
