-- 今日会议示例（可重复执行，按当天日期插入）
-- 用法: mysql -u root -p enterprise_collaboration < 006-meeting-today-seed.sql

USE enterprise_collaboration;

INSERT INTO sys_meeting (title, room, creator_id, date, start_time, end_time, attendees, status, description)
SELECT '产品周会', 'A301 (20人)', 1, CURDATE(), '10:00', '11:00', '张三,李四', 'confirmed', '同步本周迭代与风险'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_meeting WHERE date = CURDATE() AND title = '产品周会' AND deleted = 0
);

INSERT INTO sys_meeting (title, room, creator_id, date, start_time, end_time, attendees, status, description)
SELECT '需求评审', 'B205 (8人)', 1, CURDATE(), '14:30', '15:30', '产品,研发,测试', 'confirmed', '评审会议预约相关需求'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_meeting WHERE date = CURDATE() AND title = '需求评审' AND deleted = 0
);

INSERT INTO sys_meeting (title, room, creator_id, date, start_time, end_time, attendees, status, description)
SELECT '项目站会', 'C102 (12人)', 1, CURDATE(), '09:30', '09:45', '项目组全员', 'confirmed', '15 分钟站会'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_meeting WHERE date = CURDATE() AND title = '项目站会' AND deleted = 0
);
