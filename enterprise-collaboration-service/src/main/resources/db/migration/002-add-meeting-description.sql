-- 手动执行：为已有库补充会议备注字段（新库已由 schema.sql 包含该列）
ALTER TABLE sys_meeting ADD COLUMN description TEXT NULL;
