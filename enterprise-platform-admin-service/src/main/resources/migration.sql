-- Data migration: enterprise_gateway → enterprise_platform
-- Run after schema.sql creates the tables

-- Migrate departments
INSERT INTO enterprise_platform.sys_dept (id, name, parent_id, created_at, updated_at)
SELECT id, name, parent_id, created_at, updated_at
FROM enterprise_gateway.sys_dept;

-- Migrate permissions
INSERT INTO enterprise_platform.sys_permission (id, code, name, created_at, updated_at)
SELECT id, code, name, created_at, updated_at
FROM enterprise_gateway.sys_permission;

-- Migrate roles
INSERT INTO enterprise_platform.sys_role (id, code, name, created_at, updated_at)
SELECT id, code, name, created_at, updated_at
FROM enterprise_gateway.sys_role;

-- Migrate role_permission
INSERT INTO enterprise_platform.sys_role_permission (role_id, permission_id)
SELECT role_id, permission_id
FROM enterprise_gateway.sys_role_permission;

-- Migrate users (JPA: dept is ManyToOne stored as dept_id)
INSERT INTO enterprise_platform.sys_user (id, username, password_hash, real_name, dept_id, enabled, created_at, updated_at)
SELECT id, username, password_hash, real_name, dept_id, enabled, created_at, updated_at
FROM enterprise_gateway.sys_user;

-- Migrate user_role
INSERT INTO enterprise_platform.sys_user_role (user_id, role_id)
SELECT user_id, role_id
FROM enterprise_gateway.sys_user_role;

-- Migrate operation logs
INSERT INTO enterprise_platform.sys_op_log (id, user_id, username, action, method, path, detail, created_at)
SELECT id, user_id, username, action, method, path, detail, created_at
FROM enterprise_gateway.sys_op_log;
