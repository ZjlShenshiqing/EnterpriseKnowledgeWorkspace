-- kb_category 知识分类表
CREATE TABLE IF NOT EXISTS kb_category (
    id BIGINT NOT NULL PRIMARY KEY,
    parent_id BIGINT NULL COMMENT '父分类ID（树形结构）',
    category_name VARCHAR(255) NOT NULL COMMENT '分类名称',
    category_type VARCHAR(64) NULL COMMENT '分类类型',
    department_id BIGINT NULL COMMENT '所属部门ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识分类表';
