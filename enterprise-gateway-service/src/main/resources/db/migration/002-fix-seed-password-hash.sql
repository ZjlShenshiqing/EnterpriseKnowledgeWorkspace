/**
 * 修复种子数据中无效的 BCrypt 示例哈希（原 hash 无法匹配任何明文密码）。
 * 统一重置测试账号密码为 123456。
 */
UPDATE sys_user
SET password_hash = '$2b$10$4ya3hhnFrBYhLge0QMKj.O4pg3pWI37wOnKi783bwmyYRdRdvHajO'
WHERE username IN ('admin', 'zhangsan', 'lisi', 'wangwu', 'zhaoliu', 'zhangjilin');
