-- Seata Server 事务状态库初始化脚本
-- 适用版本: Seata 2.x (MySQL 8.x)
-- 使用方式: 在 Seata Server 配置的数据库中执行本脚本

CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE seata;

-- 全局事务表
CREATE TABLE IF NOT EXISTS global_table (
    xid VARCHAR(128) NOT NULL,
    transaction_id BIGINT,
    status TINYINT NOT NULL,
    application_id VARCHAR(64),
    transaction_service_group VARCHAR(64),
    transaction_name VARCHAR(128),
    timeout INT,
    begin_time BIGINT,
    application_data VARCHAR(2000),
    gmt_create DATETIME,
    gmt_modified DATETIME,
    PRIMARY KEY (xid),
    KEY idx_status_gmt_modified (status, gmt_modified),
    KEY idx_transaction_id (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分支事务表
CREATE TABLE IF NOT EXISTS branch_table (
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    transaction_id BIGINT,
    resource_group_id VARCHAR(32),
    resource_id VARCHAR(256),
    branch_type VARCHAR(8),
    status TINYINT,
    client_id VARCHAR(64),
    application_data VARCHAR(2000),
    gmt_create DATETIME(6),
    gmt_modified DATETIME(6),
    PRIMARY KEY (branch_id),
    KEY idx_xid (xid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 全局锁表
CREATE TABLE IF NOT EXISTS lock_table (
    row_key VARCHAR(128) NOT NULL,
    xid VARCHAR(128),
    transaction_id BIGINT,
    branch_id BIGINT NOT NULL,
    resource_id VARCHAR(256),
    table_name VARCHAR(32),
    pk VARCHAR(36),
    status TINYINT NOT NULL DEFAULT 0,
    gmt_create DATETIME,
    gmt_modified DATETIME,
    PRIMARY KEY (row_key),
    KEY idx_branch_id (branch_id),
    KEY idx_xid_and_branch_id (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分布式锁表 (Seata Server 集群选举)
CREATE TABLE IF NOT EXISTS distributed_lock (
    lock_key VARCHAR(20) NOT NULL,
    lock_value VARCHAR(20) NOT NULL,
    expire BIGINT,
    PRIMARY KEY (lock_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
