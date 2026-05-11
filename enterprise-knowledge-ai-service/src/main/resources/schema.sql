-- enterprise-knowledge-ai-service 知识库数据库初始化
-- MySQL 8+，库名由 JDBC URL 指定（如 enterprise_knowledge_ai）
-- 请先执行：CREATE DATABASE IF NOT EXISTS enterprise_knowledge_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS kb_agent_message;
DROP TABLE IF EXISTS kb_agent_session;
DROP TABLE IF EXISTS kb_document_chunk_log;
DROP TABLE IF EXISTS kb_document_chunk;
DROP TABLE IF EXISTS kb_document_permission;
DROP TABLE IF EXISTS kb_document;
DROP TABLE IF EXISTS kb_knowledge_base;
DROP TABLE IF EXISTS kb_category;

CREATE TABLE kb_category (
    id BIGINT NOT NULL PRIMARY KEY,
    parent_id BIGINT NULL,
    category_name VARCHAR(255) NOT NULL,
    category_type VARCHAR(64) NULL,
    department_id BIGINT NULL,
    sort_order INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_knowledge_base (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    embedding_model VARCHAR(128) NULL,
    collection_name VARCHAR(128) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    KEY idx_kb_base_owner (owner_id),
    KEY idx_kb_base_name (name),
    KEY idx_kb_base_collection (collection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_document (
    id BIGINT NOT NULL PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    category_id BIGINT NULL,
    kb_id BIGINT NULL,
    owner_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    file_name VARCHAR(512) NULL,
    file_url VARCHAR(1024) NULL,
    file_type VARCHAR(128) NULL,
    file_size BIGINT NULL,
    summary VARCHAR(1024) NULL,
    content_text LONGTEXT NULL,
    tags VARCHAR(512) NULL,
    permission_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    current_version INT DEFAULT 1,
    chunk_count INT DEFAULT 0,
    enabled INT DEFAULT 1,
    process_mode VARCHAR(32) DEFAULT 'CHUNK',
    chunk_strategy VARCHAR(64) NULL,
    chunk_config LONGTEXT NULL,
    pipeline_id VARCHAR(128) NULL,
    source_type VARCHAR(32) DEFAULT 'FILE',
    source_location VARCHAR(1024) NULL,
    schedule_enabled INT DEFAULT 0,
    metadata LONGTEXT NULL,
    filter_tags LONGTEXT NULL,
    schedule_cron VARCHAR(128) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    KEY idx_kb_document_owner (owner_id),
    KEY idx_kb_document_category (category_id),
    KEY idx_kb_document_kb (kb_id),
    KEY idx_kb_document_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_document_permission (
    id BIGINT NOT NULL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    permission_target_type VARCHAR(64) NOT NULL,
    permission_target_id BIGINT NULL,
    permission_level VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_kb_doc_perm_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_document_chunk (
    id BIGINT NOT NULL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_text LONGTEXT NULL,
    content_hash VARCHAR(64) NULL,
    char_count INT NULL,
    token_count INT NULL,
    vector_id VARCHAR(256) NULL,
    enabled INT DEFAULT 1,
    metadata_json LONGTEXT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_kb_chunk_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_document_chunk_log (
    id BIGINT NOT NULL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL,
    process_mode VARCHAR(32) NULL,
    chunk_strategy VARCHAR(64) NULL,
    pipeline_id VARCHAR(128) NULL,
    chunk_count INT DEFAULT 0,
    extract_duration_ms BIGINT NULL,
    chunk_duration_ms BIGINT NULL,
    embed_duration_ms BIGINT NULL,
    persist_duration_ms BIGINT NULL,
    total_duration_ms BIGINT NULL,
    error_message LONGTEXT NULL,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    KEY idx_chunk_log_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_agent_session (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(256) NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_agent_message (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NULL,
    tool_name VARCHAR(128) NULL,
    tool_input LONGTEXT NULL,
    tool_output LONGTEXT NULL,
    token_count INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_message_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO kb_category (id, parent_id, category_name, category_type, sort_order, status, created_at, updated_at, deleted)
SELECT 1001, NULL, '默认分类', 'COMMON', 0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM kb_category WHERE id = 1001);
