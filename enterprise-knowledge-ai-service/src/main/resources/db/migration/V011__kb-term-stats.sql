-- BM25 词项统计表
-- 用法: mysql -u root -p enterprise_knowledge_ai < V011__kb-term-stats.sql

USE enterprise_knowledge_ai;

CREATE TABLE IF NOT EXISTS kb_term_stats (
    term      VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '归一化后的词项',
    doc_count INT          NOT NULL DEFAULT 0 COMMENT '包含该词项的文档（chunk）数量',
    KEY idx_doc_count (doc_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BM25 词项-文档频率统计表';
