-- 知识库 + 流水线示例（与关键词映射、意图树场景对齐）
-- 可重复执行：已存在同名知识库或集合则跳过
-- 用法: mysql -u root -p enterprise_knowledge_ai < 010-kb-pipeline-seed.sql
--
-- 推荐：也可通过 API 创建（会自动生成流水线）：
--   POST /api/kb/bases  { "name":"财务制度知识库", "collectionName":"kb_finance" }

USE enterprise_knowledge_ai;

-- 确保流水线表存在
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

-- 知识库（固定 ID 便于流水线关联；若 ID 冲突请改用 API 创建）
INSERT INTO kb_knowledge_base (id, name, embedding_model, collection_name, owner_id, created_at, updated_at, deleted)
SELECT 910001, '财务制度知识库', NULL, 'kb_finance', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base WHERE name = '财务制度知识库' AND deleted = 0);

INSERT INTO kb_knowledge_base (id, name, embedding_model, collection_name, owner_id, created_at, updated_at, deleted)
SELECT 910002, '人事制度知识库', NULL, 'kb_hr', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base WHERE name = '人事制度知识库' AND deleted = 0);

INSERT INTO kb_knowledge_base (id, name, embedding_model, collection_name, owner_id, created_at, updated_at, deleted)
SELECT 910003, '协作流程知识库', NULL, 'kb_collab', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base WHERE name = '协作流程知识库' AND deleted = 0);

INSERT INTO kb_knowledge_base (id, name, embedding_model, collection_name, owner_id, created_at, updated_at, deleted)
SELECT 910004, 'IT 运维知识库', NULL, 'kb_it', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base WHERE name = 'IT 运维知识库' AND deleted = 0);

INSERT INTO kb_knowledge_base (id, name, embedding_model, collection_name, owner_id, created_at, updated_at, deleted)
SELECT 910005, '法务合规知识库', NULL, 'kb_legal', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base WHERE name = '法务合规知识库' AND deleted = 0);

INSERT INTO kb_knowledge_base (id, name, embedding_model, collection_name, owner_id, created_at, updated_at, deleted)
SELECT 910006, '平台使用手册', NULL, 'kb_platform', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base WHERE name = '平台使用手册' AND deleted = 0);

-- 对应流水线（与知识库一一对应）
INSERT INTO kb_pipeline (id, knowledge_base_id, name, description, stages,
                         chunk_strategy, vector_enabled, embedding_model,
                         status, created_at, updated_at, deleted)
SELECT kb.id, kb.id,
       CONCAT(kb.name, ' · 文档入库链路'),
       '覆盖上传、解析、分块、向量写入和主表回写',
       JSON_ARRAY('上传', '解析', '分块', '向量写入', '回写'),
       'PARAGRAPH', 0, COALESCE(kb.embedding_model, ''),
       'ACTIVE', NOW(), NOW(), 0
FROM kb_knowledge_base kb
WHERE kb.deleted = 0
  AND kb.name IN ('财务制度知识库','人事制度知识库','协作流程知识库','IT 运维知识库','法务合规知识库','平台使用手册')
  AND NOT EXISTS (
      SELECT 1 FROM kb_pipeline p WHERE p.knowledge_base_id = kb.id AND p.deleted = 0
  );
