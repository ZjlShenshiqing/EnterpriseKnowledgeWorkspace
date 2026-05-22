-- enterprise-collaboration-service 协同服务数据库初始化
-- 请先执行: CREATE DATABASE IF NOT EXISTS enterprise_collaboration DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;

CREATE TABLE sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    real_name VARCHAR(64) NULL,
    dept_id BIGINT NULL,
    is_admin TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY idx_user_username (username),
    KEY idx_user_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_dept (id, name) VALUES (1, '技术部'), (2, '产品部'), (3, '设计部');

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, is_admin)
VALUES (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 1, 1);

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, is_admin)
VALUES (2, 'zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 1, 0);

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, is_admin)
VALUES (3, 'lisi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 2, 0);

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, is_admin)
VALUES (4, 'wangwu', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王五', 1, 0);

INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, is_admin)
VALUES (5, 'zhaoliu', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵六', 3, 0);

DROP TABLE IF EXISTS sys_announcement;
CREATE TABLE sys_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    publisher_id BIGINT NOT NULL,
    publisher_name VARCHAR(64) NULL,
    is_pinned TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS im_conversation;
CREATE TABLE im_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'group',
    avatar VARCHAR(256) NULL,
    created_by BIGINT NOT NULL,
    last_msg_content VARCHAR(512) NULL,
    last_msg_sender VARCHAR(64) NULL,
    last_msg_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS im_conversation_member;
CREATE TABLE im_conversation_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_conv_user (conversation_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS im_message;
CREATE TABLE im_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(64) NULL,
    content TEXT NOT NULL,
    msg_type VARCHAR(16) DEFAULT 'text',
    status VARCHAR(16) NOT NULL DEFAULT 'SENT',
    mq_msg_id VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_conv (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS im_message_read;
CREATE TABLE im_message_read (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    last_read_msg_id BIGINT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS im_message_file;
CREATE TABLE im_message_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    oss_key VARCHAR(512) NOT NULL,
    thumb_oss_key VARCHAR(512) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_task;
CREATE TABLE sys_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    description TEXT NULL,
    creator_id BIGINT NOT NULL,
    assignee_id BIGINT NULL,
    priority VARCHAR(16) DEFAULT 'medium',
    status VARCHAR(32) DEFAULT 'todo',
    due_date DATE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_assignee (assignee_id),
    KEY idx_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_task_comment;
CREATE TABLE sys_task_comment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64) NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_comment_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_approval_request;
CREATE TABLE sys_approval_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64) NULL,
    title VARCHAR(256) NOT NULL,
    form_data JSON NULL,
    status VARCHAR(32) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_approval_user (user_id),
    KEY idx_approval_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_approval_record;
CREATE TABLE sys_approval_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_name VARCHAR(64) NULL,
    action VARCHAR(16) NOT NULL,
    comment VARCHAR(512) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_record_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_meeting;
CREATE TABLE sys_meeting (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    room VARCHAR(128) NULL,
    creator_id BIGINT NOT NULL,
    date DATE NULL,
    start_time VARCHAR(8) NULL,
    end_time VARCHAR(8) NULL,
    attendees TEXT NULL,
    description TEXT NULL,
    status VARCHAR(32) DEFAULT 'confirmed',
    join_url VARCHAR(1024) NULL,
    meeting_id VARCHAR(128) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_todo;
CREATE TABLE sys_todo (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    user_id BIGINT NOT NULL,
    priority VARCHAR(16) DEFAULT 'normal',
    due_date DATE NULL,
    done TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_doc;
CREATE TABLE sys_doc (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    content LONGTEXT NULL,
    updated_by BIGINT NULL,
    updated_by_name VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
