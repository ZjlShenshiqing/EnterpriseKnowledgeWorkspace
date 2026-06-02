-- ============================================================
-- Enterprise Knowledge Workspace 全量数据库初始化
-- 包含 2 个数据库共 16 张表
-- MySQL 8+
-- ============================================================

-- ============================================================
-- 数据库 enterprise_knowledge_ai（知识库微服务）
-- ============================================================
-- CREATE DATABASE IF NOT EXISTS enterprise_knowledge_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE enterprise_knowledge_ai;

-- -------------------- kb_category 知识分类表 --------------------
DROP TABLE IF EXISTS kb_category;
CREATE TABLE kb_category (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
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

-- -------------------- kb_knowledge_base 逻辑知识库表 --------------------
DROP TABLE IF EXISTS kb_knowledge_base;
CREATE TABLE kb_knowledge_base (
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

-- -------------------- kb_document 知识文档表 --------------------
DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    title VARCHAR(512) NOT NULL COMMENT '文档标题',
    category_id BIGINT NULL COMMENT '分类ID',
    kb_id BIGINT NULL COMMENT '所属知识库ID（为空时使用默认Milvus集合）',
    owner_id BIGINT NOT NULL COMMENT '上传者用户ID',
    department_id BIGINT NULL COMMENT '所属部门ID',
    file_name VARCHAR(512) NULL COMMENT '原始文件名',
    file_url VARCHAR(1024) NULL COMMENT '文件存储路径',
    file_type VARCHAR(128) NULL COMMENT 'MIME类型（Tika探测后覆盖）',
    file_size BIGINT NULL COMMENT '文件大小（字节）',
    summary VARCHAR(1024) NULL COMMENT '摘要（分块后取前200字）',
    content_text LONGTEXT NULL COMMENT 'Tika解析后的完整正文',
    tags VARCHAR(512) NULL COMMENT '标签（逗号分隔）',
    permission_type VARCHAR(64) NOT NULL COMMENT '权限类型 ALL/DEPARTMENT/PROJECT/USER/ADMIN',
    status VARCHAR(64) NOT NULL COMMENT '文档状态 PENDING/RUNNING/SUCCESS/FAILED等',
    current_version INT DEFAULT 1 COMMENT '当前版本号',
    chunk_count INT DEFAULT 0 COMMENT '切片数量（查询计数）',
    enabled INT DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    process_mode VARCHAR(32) DEFAULT 'CHUNK' COMMENT '处理模式 CHUNK/PIPELINE',
    chunk_strategy VARCHAR(64) NULL COMMENT '分块策略 FIXED_SIZE/PARAGRAPH',
    chunk_config LONGTEXT NULL COMMENT '分块参数JSON',
    pipeline_id VARCHAR(128) NULL COMMENT 'Pipeline定义ID（PIPELINE模式使用）',
    source_type VARCHAR(32) DEFAULT 'FILE' COMMENT '来源类型 FILE/URL',
    source_location VARCHAR(1024) NULL COMMENT 'URL来源地址',
    schedule_enabled INT DEFAULT 0 COMMENT '定时拉取开关 0-关闭 1-开启',
    schedule_cron VARCHAR(128) NULL COMMENT '定时Cron表达式',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    KEY idx_kb_document_owner (owner_id),
    KEY idx_kb_document_category (category_id),
    KEY idx_kb_document_kb (kb_id),
    KEY idx_kb_document_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

-- -------------------- kb_document_permission 文档权限明细表 --------------------
DROP TABLE IF EXISTS kb_document_permission;
CREATE TABLE kb_document_permission (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    permission_target_type VARCHAR(64) NOT NULL COMMENT '授权对象类型 USER/PROJECT',
    permission_target_id BIGINT NULL COMMENT '授权对象ID（用户ID或项目ID）',
    permission_level VARCHAR(64) NULL COMMENT '权限级别（当前仅READ）',
    created_by BIGINT NULL COMMENT '创建人用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_kb_doc_perm_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档权限明细表（物理删除）';

-- -------------------- kb_document_chunk 文档切片表 --------------------
DROP TABLE IF EXISTS kb_document_chunk;
CREATE TABLE kb_document_chunk (
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

-- -------------------- kb_document_chunk_log 文档分块任务日志表 --------------------
DROP TABLE IF EXISTS kb_document_chunk_log;
CREATE TABLE kb_document_chunk_log (
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

-- -------------------- kb_agent_session Agent对话会话表 --------------------
DROP TABLE IF EXISTS kb_agent_session;
CREATE TABLE kb_agent_session (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(256) NULL COMMENT '会话标题（由首条消息截取）',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/ARCHIVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_agent_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent对话会话表';

-- -------------------- kb_agent_message Agent对话消息表 --------------------
DROP TABLE IF EXISTS kb_agent_message;
CREATE TABLE kb_agent_message (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键（雪花ID）',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(16) NOT NULL COMMENT '消息角色 user/assistant/tool',
    content LONGTEXT NULL COMMENT '文本内容（user或assistant消息）',
    tool_name VARCHAR(128) NULL COMMENT '工具名（role=tool时）',
    tool_input LONGTEXT NULL COMMENT '工具入参JSON（role=tool时）',
    tool_output LONGTEXT NULL COMMENT '工具返回结果JSON（role=tool时）',
    token_count INT NULL COMMENT 'Token用量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_agent_message_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent对话消息表';

-- -------------------- kb_intent_node 意图节点表 --------------------
DROP TABLE IF EXISTS kb_intent_node;
CREATE TABLE kb_intent_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parent_id BIGINT NULL COMMENT '父节点ID，NULL为根场景',
    name VARCHAR(128) NOT NULL COMMENT '节点名称',
    level TINYINT NOT NULL DEFAULT 1 COMMENT '层级 1=场景 2=意图',
    sort_order INT DEFAULT 0 COMMENT '同级排序',
    description VARCHAR(512) NULL COMMENT '节点说明',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_intent_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图节点表';

-- -------------------- kb_intent_rule 意图匹配规则表 --------------------
DROP TABLE IF EXISTS kb_intent_rule;
CREATE TABLE kb_intent_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '意图节点ID',
    rule_type VARCHAR(16) NOT NULL COMMENT 'keyword / regex',
    expression VARCHAR(256) NOT NULL COMMENT '关键词或正则表达式',
    weight DOUBLE NOT NULL DEFAULT 1.0 COMMENT '匹配权重',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rule_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图匹配规则表';

-- -------------------- kb_intent_kb_rel 意图知识库关联表 --------------------
DROP TABLE IF EXISTS kb_intent_kb_rel;
CREATE TABLE kb_intent_kb_rel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '意图节点ID',
    kb_id BIGINT NOT NULL COMMENT '知识库ID',
    weight DOUBLE NOT NULL DEFAULT 1.0 COMMENT '检索权重',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intent_kb (node_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图知识库关联表';

-- -------------------- 种子数据 --------------------
INSERT INTO kb_category (id, parent_id, category_name, category_type, sort_order, status, created_at, updated_at, deleted)
SELECT 1001, NULL, '默认分类', 'COMMON', 0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM kb_category WHERE id = 1001);

-- ============================================================
-- 数据库 enterprise_gateway（网关服务）
-- ============================================================
CREATE DATABASE IF NOT EXISTS enterprise_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE enterprise_gateway;

-- -------------------- sys_dept 部门表 --------------------
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '部门名称（唯一）',
    parent_id BIGINT NULL COMMENT '父部门ID（根部门可为空）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY idx_sys_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- -------------------- sys_user 用户表 --------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名（唯一）',
    password_hash VARCHAR(200) NOT NULL COMMENT '密码哈希（BCrypt）',
    real_name VARCHAR(64) NULL COMMENT '真实姓名',
    dept_id BIGINT NULL COMMENT '所属部门ID',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY idx_sys_user_username (username),
    KEY idx_sys_user_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- -------------------- sys_role 角色表 --------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '角色编码（唯一）',
    name VARCHAR(128) NOT NULL COMMENT '角色名称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY idx_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- -------------------- sys_permission 权限表 --------------------
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(128) NOT NULL COMMENT '权限编码（domain:action形式）',
    name VARCHAR(200) NOT NULL COMMENT '权限名称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY idx_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

-- -------------------- sys_user_role 用户角色关联表 --------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- -------------------- sys_role_permission 角色权限关联表 --------------------
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_perm_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- -------------------- sys_op_log 操作日志表 --------------------
DROP TABLE IF EXISTS sys_op_log;
CREATE TABLE sys_op_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NULL COMMENT '操作用户ID',
    username VARCHAR(64) NOT NULL COMMENT '操作用户名',
    action VARCHAR(32) NOT NULL COMMENT '操作类型',
    method VARCHAR(32) NOT NULL COMMENT 'HTTP方法',
    path VARCHAR(512) NOT NULL COMMENT '请求路径',
    detail VARCHAR(2000) NULL COMMENT '操作详情',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_sys_op_log_user_id (user_id),
    KEY idx_sys_op_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作审计日志表';

-- -------------------- sys_token_blacklist Token黑名单表 --------------------
DROP TABLE IF EXISTS sys_token_blacklist;
CREATE TABLE sys_token_blacklist (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    token_hash VARCHAR(64) NOT NULL COMMENT 'token哈希值（SHA-256 hex 64）',
    expires_at TIMESTAMP NOT NULL COMMENT 'token过期时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
    UNIQUE KEY idx_sys_token_blacklist_token_hash (token_hash),
    KEY idx_sys_token_blacklist_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT Token黑名单表';

-- -------------------- Gateway 种子数据 --------------------
INSERT INTO sys_dept (id, name, parent_id) VALUES
(1, '技术部', NULL),
(2, '产品部', NULL),
(3, '设计部', NULL);

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, enabled) VALUES
(1, 'admin',    '$2b$10$4ya3hhnFrBYhLge0QMKj.O4pg3pWI37wOnKi783bwmyYRdRdvHajO', '系统管理员', 1, 1),
(2, 'zhangsan', '$2b$10$4ya3hhnFrBYhLge0QMKj.O4pg3pWI37wOnKi783bwmyYRdRdvHajO', '张三',     1, 1),
(3, 'lisi',     '$2b$10$4ya3hhnFrBYhLge0QMKj.O4pg3pWI37wOnKi783bwmyYRdRdvHajO', '李四',     2, 1),
(4, 'wangwu',   '$2b$10$4ya3hhnFrBYhLge0QMKj.O4pg3pWI37wOnKi783bwmyYRdRdvHajO', '王五',     1, 0),
(5, 'zhaoliu',  '$2b$10$4ya3hhnFrBYhLge0QMKj.O4pg3pWI37wOnKi783bwmyYRdRdvHajO', '赵六',     3, 1);

INSERT INTO sys_role (id, code, name) VALUES
(1, 'admin',   '系统管理员'),
(2, 'manager', '部门主管'),
(3, 'user',    '普通员工'),
(4, 'finance', '财务');

INSERT INTO sys_permission (id, code, name) VALUES
(1, 'system:user:read',   '查看用户'),
(2, 'system:user:write',  '管理用户'),
(3, 'system:role:read',   '查看角色'),
(4, 'system:role:write',  '管理角色'),
(5, 'system:dept:read',   '查看部门'),
(6, 'system:dept:write',  '管理部门'),
(7, 'system:log:read',    '查看日志'),
(8, 'kb:document:read',   '查看文档'),
(9, 'kb:document:write',  '管理文档');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9),
(2, 1), (2, 3), (2, 5), (2, 8), (2, 9),
(3, 1), (3, 5), (3, 8);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 3),
(5, 3);

-- ============================================================
-- 数据库 enterprise_collaboration（协同服务）
-- ============================================================
-- CREATE DATABASE IF NOT EXISTS enterprise_collaboration DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE enterprise_collaboration;

DROP TABLE IF EXISTS sys_doc;
DROP TABLE IF EXISTS sys_doc_operation;
DROP TABLE IF EXISTS sys_doc_comment;
DROP TABLE IF EXISTS sys_doc_share_link;
DROP TABLE IF EXISTS sys_doc_collaborator;
DROP TABLE IF EXISTS sys_todo;
DROP TABLE IF EXISTS sys_meeting;
DROP TABLE IF EXISTS wf_record;
DROP TABLE IF EXISTS wf_task;
DROP TABLE IF EXISTS wf_instance;
DROP TABLE IF EXISTS wf_node_approver;
DROP TABLE IF EXISTS wf_node;
DROP TABLE IF EXISTS wf_template;
DROP TABLE IF EXISTS sys_approval_record;
DROP TABLE IF EXISTS sys_approval_request;
DROP TABLE IF EXISTS sys_task_comment;
DROP TABLE IF EXISTS sys_task;
DROP TABLE IF EXISTS im_message_file;
DROP TABLE IF EXISTS im_message_read;
DROP TABLE IF EXISTS im_message;
DROP TABLE IF EXISTS im_conversation_member;
DROP TABLE IF EXISTS im_conversation;
DROP TABLE IF EXISTS sys_announcement;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;
DROP TABLE IF EXISTS kb_intent_kb_rel;
DROP TABLE IF EXISTS kb_intent_rule;
DROP TABLE IF EXISTS kb_intent_node;

CREATE TABLE sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '部门名称',
    parent_id BIGINT NULL COMMENT '父部门ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(200) NOT NULL COMMENT 'BCrypt密码哈希',
    real_name VARCHAR(64) NULL COMMENT '真实姓名',
    dept_id BIGINT NULL COMMENT '部门ID',
    is_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否管理员',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_user_username (username),
    KEY idx_user_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE sys_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(256) NOT NULL COMMENT '公告标题',
    content TEXT NOT NULL COMMENT '公告内容',
    publisher_id BIGINT NOT NULL COMMENT '发布人ID',
    publisher_name VARCHAR(64) NULL COMMENT '发布人姓名',
    is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

CREATE TABLE im_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(128) NULL COMMENT '会话名称',
    type VARCHAR(16) NOT NULL DEFAULT 'group' COMMENT '会话类型 group/private',
    avatar VARCHAR(256) NULL COMMENT '头像',
    created_by BIGINT NOT NULL COMMENT '创建人',
    last_msg_content VARCHAR(512) NULL COMMENT '最后消息摘要',
    last_msg_sender VARCHAR(64) NULL COMMENT '最后消息发送者',
    last_msg_at TIMESTAMP NULL COMMENT '最后消息时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

CREATE TABLE im_conversation_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_conv_user (conversation_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话成员表';

CREATE TABLE im_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送人ID',
    sender_name VARCHAR(64) NULL COMMENT '发送人姓名',
    content TEXT NOT NULL COMMENT '消息内容',
    msg_type VARCHAR(16) DEFAULT 'text' COMMENT '消息类型',
    status VARCHAR(16) NOT NULL DEFAULT 'SENT' COMMENT '消息状态 SENDING/SENT/DELIVERED/READ/FAILED',
    mq_msg_id VARCHAR(64) NULL COMMENT 'RocketMQ 消息ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_conv (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

CREATE TABLE im_message_read (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    last_read_msg_id BIGINT NOT NULL COMMENT '最后已读消息ID',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息已读追踪表';

CREATE TABLE im_message_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    message_id BIGINT NOT NULL COMMENT '消息ID',
    file_name VARCHAR(256) NOT NULL COMMENT '文件名',
    file_size BIGINT NOT NULL COMMENT '文件大小',
    file_type VARCHAR(64) NOT NULL COMMENT '文件MIME类型',
    oss_key VARCHAR(512) NOT NULL COMMENT 'OSS存储路径',
    thumb_oss_key VARCHAR(512) NULL COMMENT '缩略图OSS路径',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msgfile_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息文件附件表';

CREATE TABLE sys_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(256) NOT NULL COMMENT '任务标题',
    description TEXT NULL COMMENT '任务描述',
    creator_id BIGINT NOT NULL COMMENT '创建人ID',
    assignee_id BIGINT NULL COMMENT '负责人ID',
    priority VARCHAR(16) DEFAULT 'medium' COMMENT '优先级 high/medium/low',
    status VARCHAR(32) DEFAULT 'todo' COMMENT '状态 todo/in_progress/review/done',
    due_date DATE NULL COMMENT '截止日期',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_assignee (assignee_id),
    KEY idx_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

CREATE TABLE sys_task_comment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '评论人ID',
    user_name VARCHAR(64) NULL COMMENT '评论人姓名',
    content TEXT NOT NULL COMMENT '评论内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_comment_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务评论表';

CREATE TABLE sys_approval_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    type VARCHAR(32) NOT NULL COMMENT '审批类型 leave/expense',
    user_id BIGINT NOT NULL COMMENT '申请人ID',
    user_name VARCHAR(64) NULL COMMENT '申请人姓名',
    title VARCHAR(256) NOT NULL COMMENT '审批标题',
    form_data JSON NULL COMMENT '表单数据JSON',
    status VARCHAR(32) DEFAULT 'pending' COMMENT '状态',
    workflow_instance_id BIGINT NULL COMMENT '工作流实例ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    KEY idx_approval_user (user_id),
    KEY idx_approval_status (status),
    KEY idx_approval_workflow_instance (workflow_instance_id),
    KEY idx_approval_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批申请表';

CREATE TABLE sys_approval_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    request_id BIGINT NOT NULL COMMENT '审批请求ID',
    approver_id BIGINT NOT NULL COMMENT '审批人ID',
    approver_name VARCHAR(64) NULL COMMENT '审批人姓名',
    action VARCHAR(16) NOT NULL COMMENT '操作 approve/reject',
    comment VARCHAR(512) NULL COMMENT '审批意见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_record_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

CREATE TABLE wf_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '模板编码 leave/expense',
    name VARCHAR(128) NOT NULL COMMENT '模板名称',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型 approval',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_wf_template_code (code, business_type),
    KEY idx_wf_template_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流模板表';

CREATE TABLE wf_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    node_key VARCHAR(64) NOT NULL COMMENT '节点编码',
    node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(32) NOT NULL COMMENT 'START/APPROVAL/END',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    approval_mode VARCHAR(32) NOT NULL DEFAULT 'ANY' COMMENT '审批模式',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_wf_node_key (template_id, node_key),
    KEY idx_wf_node_template_sort (template_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点表';

CREATE TABLE wf_node_approver (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '节点ID',
    approver_type VARCHAR(32) NOT NULL COMMENT 'USER/ROLE',
    approver_id BIGINT NOT NULL COMMENT '用户ID或角色ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wf_node_approver_node (node_id),
    KEY idx_wf_node_approver_target (approver_type, approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点审批人表';

CREATE TABLE wf_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    business_id BIGINT NOT NULL COMMENT '业务ID',
    starter_id BIGINT NOT NULL COMMENT '发起人ID',
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING/APPROVED/REJECTED/CANCELLED',
    current_node_id BIGINT NULL COMMENT '当前节点ID',
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_wf_instance_business (business_type, business_id),
    KEY idx_wf_instance_starter (starter_id),
    KEY idx_wf_instance_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流实例表';

CREATE TABLE wf_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    instance_id BIGINT NOT NULL COMMENT '实例ID',
    node_id BIGINT NOT NULL COMMENT '节点ID',
    assignee_type VARCHAR(32) NOT NULL COMMENT 'USER/ROLE',
    assignee_id BIGINT NOT NULL COMMENT '用户ID或角色ID',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CLOSED',
    claimed_by BIGINT NULL COMMENT '实际处理人',
    handled_at TIMESTAMP NULL,
    comment VARCHAR(512) NULL COMMENT '审批意见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_wf_task_instance_node (instance_id, node_id),
    KEY idx_wf_task_assignee (assignee_type, assignee_id, status),
    KEY idx_wf_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流任务表';

CREATE TABLE wf_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    instance_id BIGINT NOT NULL COMMENT '实例ID',
    node_id BIGINT NULL COMMENT '节点ID',
    task_id BIGINT NULL COMMENT '任务ID',
    operator_id BIGINT NULL COMMENT '操作人ID',
    action VARCHAR(32) NOT NULL COMMENT 'START/APPROVE/REJECT/AUTO_CLOSE/COMPLETE',
    from_status VARCHAR(32) NULL COMMENT '原状态',
    to_status VARCHAR(32) NULL COMMENT '新状态',
    comment VARCHAR(512) NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wf_record_instance (instance_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流流转记录表';

INSERT INTO wf_template (code, name, business_type, enabled) VALUES
('leave', '请假审批', 'approval', 1),
('expense', '报销审批', 'approval', 1);

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'start', '开始', 'START', 0, 'ANY'
FROM wf_template t
WHERE t.code IN ('leave', 'expense');

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'manager_approve', '主管审批', 'APPROVAL', 10, 'ANY'
FROM wf_template t
WHERE t.code IN ('leave', 'expense');

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'finance_approve', '财务审批', 'APPROVAL', 20, 'ANY'
FROM wf_template t
WHERE t.code = 'expense';

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'end', '结束', 'END', CASE WHEN t.code = 'expense' THEN 30 ELSE 20 END, 'ANY'
FROM wf_template t
WHERE t.code IN ('leave', 'expense');

INSERT INTO wf_node_approver (node_id, approver_type, approver_id)
SELECT n.id, 'ROLE', r.id
FROM wf_node n
JOIN wf_template t ON n.template_id = t.id
JOIN enterprise_gateway.sys_role r ON r.code = 'manager'
WHERE n.node_key = 'manager_approve';

INSERT INTO wf_node_approver (node_id, approver_type, approver_id)
SELECT n.id, 'ROLE', r.id
FROM wf_node n
JOIN wf_template t ON n.template_id = t.id
JOIN enterprise_gateway.sys_role r ON r.code = 'finance'
WHERE t.code = 'expense'
  AND n.node_key = 'finance_approve';

CREATE TABLE sys_meeting (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(256) NOT NULL COMMENT '会议标题',
    room VARCHAR(128) NULL COMMENT '会议室',
    creator_id BIGINT NOT NULL COMMENT '创建人ID',
    date DATE NULL COMMENT '会议日期',
    start_time VARCHAR(8) NULL COMMENT '开始时间',
    end_time VARCHAR(8) NULL COMMENT '结束时间',
    attendees TEXT NULL COMMENT '参会人',
    status VARCHAR(32) DEFAULT 'confirmed' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议表';

CREATE TABLE sys_todo (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(256) NOT NULL COMMENT '待办标题',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    priority VARCHAR(16) DEFAULT 'normal' COMMENT '优先级',
    due_date DATE NULL COMMENT '截止日期',
    done TINYINT DEFAULT 0 COMMENT '是否完成',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办事项表';

CREATE TABLE sys_doc (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(256) NOT NULL COMMENT '文档标题',
    content LONGTEXT NULL COMMENT '文档内容(Quill Delta JSON)',
    version INT NOT NULL DEFAULT 0 COMMENT '当前操作版本号',
    snapshot_version INT NOT NULL DEFAULT 0 COMMENT '最后快照版本号',
    created_by BIGINT NULL COMMENT '创建者ID',
    updated_by BIGINT NULL COMMENT '最后编辑人ID',
    updated_by_name VARCHAR(64) NULL COMMENT '最后编辑人姓名',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作文档表';

-- 文档操作日志表 (OT)
CREATE TABLE IF NOT EXISTS sys_doc_operation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    version INT NOT NULL,
    operation LONGTEXT NOT NULL COMMENT 'Quill Delta JSON',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_version (doc_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档操作日志表';

-- 文档评论表
CREATE TABLE IF NOT EXISTS sys_doc_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    anchor_index INT DEFAULT NULL COMMENT '锚定起始位置',
    anchor_length INT DEFAULT NULL COMMENT '锚定长度',
    parent_id BIGINT DEFAULT NULL COMMENT '回复目标评论ID',
    resolved TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_comment (doc_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档评论表';

-- 文档分享链接表
CREATE TABLE IF NOT EXISTS sys_doc_share_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL,
    permission VARCHAR(10) NOT NULL COMMENT 'VIEW/COMMENT/EDIT',
    expired_at TIMESTAMP NULL DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分享链接表';

-- 文档协作者表
CREATE TABLE IF NOT EXISTS sys_doc_collaborator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    target_type VARCHAR(10) NOT NULL COMMENT 'USER/DEPT',
    target_id BIGINT NOT NULL,
    permission VARCHAR(10) NOT NULL COMMENT 'VIEW/COMMENT/EDIT',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_collaborator (doc_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档协作者表';

INSERT INTO sys_dept (id, name) VALUES (1, '技术部'), (2, '产品部'), (3, '设计部');

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, is_admin)
VALUES (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 1, 1),
       (2, 'zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 1, 0),
       (3, 'lisi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 2, 0),
       (4, 'wangwu', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王五', 1, 0),
       (5, 'zhaoliu', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵六', 3, 0);
