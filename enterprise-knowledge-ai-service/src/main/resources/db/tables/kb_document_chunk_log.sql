-- kb_document_chunk_log 文档分块任务日志表
CREATE TABLE IF NOT EXISTS kb_document_chunk_log (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    status VARCHAR(64) NOT NULL COMMENT '任务状态 RUNNING/SUCCESS/FAILED',
    process_mode VARCHAR(32) NULL COMMENT '处理模式',
    chunk_strategy VARCHAR(64) NULL COMMENT '分块策略',
    pipeline_id VARCHAR(128) NULL COMMENT 'Pipeline ID',
    chunk_count INT DEFAULT 0 COMMENT '生成切片数',
    extract_duration_ms BIGINT NULL COMMENT 'Tika解析耗时（毫秒）',
    chunk_duration_ms BIGINT NULL COMMENT '分块耗时（毫秒）',
    embed_duration_ms BIGINT NULL COMMENT '向量化耗时（毫秒）',
    persist_duration_ms BIGINT NULL COMMENT '持久化耗时（毫秒）',
    total_duration_ms BIGINT NULL COMMENT '总耗时（毫秒）',
    error_message LONGTEXT NULL COMMENT '失败原因',
    started_at TIMESTAMP NULL COMMENT '开始时间',
    ended_at TIMESTAMP NULL COMMENT '结束时间',
    KEY idx_chunk_log_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档分块任务日志表（物理删除）';
