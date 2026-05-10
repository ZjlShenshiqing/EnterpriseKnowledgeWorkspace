-- kb_agent_session Agent对话会话表
CREATE TABLE IF NOT EXISTS kb_agent_session (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(256) NULL COMMENT '会话标题（由首条消息截取）',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/ARCHIVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_agent_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent对话会话表';
