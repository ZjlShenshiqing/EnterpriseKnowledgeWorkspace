-- kb_document_chunk 文档切片表
CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（手动指定，与Milvus主键对齐）',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    chunk_index INT NOT NULL COMMENT '切片序号（从0递增）',
    chunk_text LONGTEXT NULL COMMENT '切片正文',
    content_hash VARCHAR(64) NULL COMMENT 'SHA-256内容哈希（十六进制）',
    char_count INT NULL COMMENT '字符数',
    token_count INT NULL COMMENT 'Token数（估算）',
    vector_id VARCHAR(256) NULL COMMENT 'Milvus向量主键引用',
    enabled INT DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    metadata_json LONGTEXT NULL COMMENT '扩展元数据JSON',
    created_by BIGINT NULL COMMENT '创建人用户ID',
    updated_by BIGINT NULL COMMENT '更新人用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_kb_chunk_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切片表（物理删除）';
