-- kb_knowledge_base 逻辑知识库表
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    name VARCHAR(255) NOT NULL COMMENT '知识库名称',
    embedding_model VARCHAR(128) NULL COMMENT '嵌入模型标识（为空时使用全局配置）',
    collection_name VARCHAR(128) NOT NULL COMMENT 'Milvus集合名',
    owner_id BIGINT NOT NULL COMMENT '创建者用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    KEY idx_kb_base_owner (owner_id),
    KEY idx_kb_base_name (name),
    KEY idx_kb_base_collection (collection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑知识库表';
