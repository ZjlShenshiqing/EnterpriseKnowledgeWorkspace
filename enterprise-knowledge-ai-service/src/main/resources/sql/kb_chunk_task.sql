-- 分块任务表
CREATE TABLE IF NOT EXISTS kb_chunk_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    document_id BIGINT NOT NULL COMMENT '文档 ID',
    operator_user_id BIGINT NOT NULL COMMENT '操作用户 ID',
    status VARCHAR(20) NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    error_message TEXT COMMENT '错误信息',
    started_at DATETIME COMMENT '开始时间',
    ended_at DATETIME COMMENT '结束时间',
    timeout_minutes INT NOT NULL DEFAULT 10 COMMENT '超时时间（分钟）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_document_id (document_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at),
    UNIQUE KEY uk_document_pending (document_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块任务表';