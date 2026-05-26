-- 流水线回填与修复（可重复执行）
-- 用法: mysql -u root -p enterprise_knowledge_ai < 009-pipeline-backfill.sql

USE enterprise_knowledge_ai;

CREATE TABLE IF NOT EXISTS kb_pipeline (
    id               BIGINT        NOT NULL COMMENT '流水线 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '知识库 ID',
    name             VARCHAR(128)  NOT NULL COMMENT '流水线名称',
    description      VARCHAR(512)  DEFAULT '' COMMENT '描述',
    stages           JSON          COMMENT '处理阶段列表',
    chunk_strategy   VARCHAR(64)   DEFAULT '' COMMENT '分块策略',
    vector_enabled   TINYINT(1)    DEFAULT 0 COMMENT '是否启用向量写入',
    embedding_model  VARCHAR(128)  DEFAULT '' COMMENT '嵌入模型名称',
    status           VARCHAR(32)   DEFAULT 'ACTIVE' COMMENT '状态',
    created_at       DATETIME      NOT NULL COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL COMMENT '更新时间',
    deleted          TINYINT(1)    DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_kb_id (knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线定义表';

INSERT INTO kb_pipeline (id, knowledge_base_id, name, description, stages,
                         chunk_strategy, vector_enabled, embedding_model,
                         status, created_at, updated_at, deleted)
SELECT
    kb.id,
    kb.id,
    CONCAT(kb.name, ' · 文档入库链路'),
    '覆盖上传、解析、分块、向量写入和主表回写',
    JSON_ARRAY('上传', '解析', '分块', '向量写入', '回写'),
    'PARAGRAPH',
    CASE WHEN kb.embedding_model IS NOT NULL AND kb.embedding_model != '' THEN 1 ELSE 0 END,
    COALESCE(kb.embedding_model, ''),
    'ACTIVE',
    COALESCE(kb.created_at, NOW()),
    COALESCE(kb.updated_at, NOW()),
    0
FROM kb_knowledge_base kb
WHERE kb.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM kb_pipeline p WHERE p.knowledge_base_id = kb.id AND p.deleted = 0
  );

UPDATE kb_pipeline p
JOIN kb_knowledge_base kb ON p.knowledge_base_id = kb.id AND kb.deleted = 0
SET
    p.name = CASE WHEN p.name IS NULL OR p.name = '' THEN CONCAT(kb.name, ' · 文档入库链路') ELSE p.name END,
    p.description = CASE WHEN p.description IS NULL OR p.description = '' THEN '覆盖上传、解析、分块、向量写入和主表回写' ELSE p.description END,
    p.stages = CASE WHEN p.stages IS NULL THEN JSON_ARRAY('上传', '解析', '分块', '向量写入', '回写') ELSE p.stages END,
    p.chunk_strategy = CASE WHEN p.chunk_strategy IS NULL OR p.chunk_strategy = '' THEN 'PARAGRAPH' ELSE p.chunk_strategy END,
    p.embedding_model = COALESCE(kb.embedding_model, ''),
    p.vector_enabled = CASE WHEN kb.embedding_model IS NOT NULL AND kb.embedding_model != '' THEN 1 ELSE 0 END,
    p.status = CASE WHEN p.status IS NULL OR p.status = '' THEN 'ACTIVE' ELSE p.status END,
    p.updated_at = NOW()
WHERE p.deleted = 0;
