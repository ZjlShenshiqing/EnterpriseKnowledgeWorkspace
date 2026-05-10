-- enterprise-knowledge-ai-service 知识库数据库初始化
-- MySQL 8+，库名由 JDBC URL 指定（如 enterprise_knowledge_ai）
-- 请先执行：CREATE DATABASE IF NOT EXISTS enterprise_knowledge_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- 建表语句已拆分至 db/tables/ 目录，由 spring.sql.init.schema-locations 通配符加载。
-- 本文件仅保留 DROP 顺序（用于重建）和种子数据。

-- DROP 顺序（按外键依赖逆序，防止重建时冲突）
DROP TABLE IF EXISTS kb_agent_message;
DROP TABLE IF EXISTS kb_agent_session;
DROP TABLE IF EXISTS kb_document_chunk_log;
DROP TABLE IF EXISTS kb_document_chunk;
DROP TABLE IF EXISTS kb_document_permission;
DROP TABLE IF EXISTS kb_document;
DROP TABLE IF EXISTS kb_knowledge_base;
DROP TABLE IF EXISTS kb_category;

-- 种子数据：默认分类
INSERT INTO kb_category (id, parent_id, category_name, category_type, sort_order, status, created_at, updated_at, deleted)
SELECT 1001, NULL, '默认分类', 'COMMON', 0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM kb_category WHERE id = 1001);
