-- kb_document_permission 文档权限明细表
CREATE TABLE IF NOT EXISTS kb_document_permission (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    permission_target_type VARCHAR(64) NOT NULL COMMENT '授权对象类型 USER/PROJECT',
    permission_target_id BIGINT NULL COMMENT '授权对象ID（用户ID或项目ID）',
    permission_level VARCHAR(64) NULL COMMENT '权限级别（当前仅READ）',
    created_by BIGINT NULL COMMENT '创建人用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_kb_doc_perm_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档权限明细表（物理删除）';
