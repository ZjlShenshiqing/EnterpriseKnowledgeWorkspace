-- 任务协同 / 待办 / 审批 示例数据（可重复执行，已存在则跳过）
-- 用法: mysql -u root -p enterprise_collaboration < 009-tasks-todos-approvals-seed.sql

USE enterprise_collaboration;

-- ========== 任务协同（看板四列） ==========

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT '竞品分析报告整理', '汇总三家竞品的功能对比与定价策略，供产品周会讨论', 1, 3, 'high', 'todo', DATE_ADD(CURDATE(), INTERVAL 5 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = '竞品分析报告整理');

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT '会议室设备巡检清单', '检查各会议室投影、麦克风、网络，输出巡检表', 1, 4, 'medium', 'todo', DATE_ADD(CURDATE(), INTERVAL 3 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = '会议室设备巡检清单');

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT 'UI 原型 v2', '根据需求评审结论更新首页与会议页交互原型', 1, 5, 'high', 'in_progress', DATE_ADD(CURDATE(), INTERVAL 7 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = 'UI 原型 v2');

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT '知识库 API 对接文档', '补充文档上传、切片状态查询、权限说明章节', 1, 2, 'medium', 'in_progress', DATE_ADD(CURDATE(), INTERVAL 4 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = '知识库 API 对接文档');

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT '周报模板优化', '统一各部门周报字段，提交行政审核', 1, 2, 'low', 'review', DATE_ADD(CURDATE(), INTERVAL 2 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = '周报模板优化');

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT 'Q1 复盘材料归档', '整理 Q1 项目复盘 PPT 与会议纪要至共享盘', 1, 3, 'medium', 'done', DATE_SUB(CURDATE(), INTERVAL 2 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = 'Q1 复盘材料归档');

INSERT INTO sys_task (title, description, creator_id, assignee_id, priority, status, due_date)
SELECT 'Dashboard 数据联调', '工作台今日会议、待办、审批统计与后端接口对齐', 1, 2, 'high', 'done', DATE_SUB(CURDATE(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE title = 'Dashboard 数据联调');

-- 任务评论
INSERT INTO sys_task_comment (task_id, user_id, user_name, content)
SELECT t.id, 2, '张三', 'API 文档初稿已写到权限章节，明天补切片状态机说明'
FROM sys_task t
WHERE t.title = '知识库 API 对接文档'
  AND NOT EXISTS (
      SELECT 1 FROM sys_task_comment c
      WHERE c.task_id = t.id AND c.content LIKE 'API 文档初稿%'
  );

INSERT INTO sys_task_comment (task_id, user_id, user_name, content)
SELECT t.id, 5, '赵六', '首页布局已更新，会议页弹窗样式待确认'
FROM sys_task t
WHERE t.title = 'UI 原型 v2'
  AND NOT EXISTS (
      SELECT 1 FROM sys_task_comment c
      WHERE c.task_id = t.id AND c.content LIKE '首页布局已更新%'
  );

INSERT INTO sys_task_comment (task_id, user_id, user_name, content)
SELECT t.id, 1, '系统管理员', '请在周五前完成，周会要用'
FROM sys_task t
WHERE t.title = '竞品分析报告整理'
  AND NOT EXISTS (
      SELECT 1 FROM sys_task_comment c
      WHERE c.task_id = t.id AND c.content LIKE '请在周五前完成%'
  );

-- ========== 个人待办（user_id=1 管理员） ==========

INSERT INTO sys_todo (title, user_id, priority, due_date, done)
SELECT '提交产品周会纪要', 1, 'high', DATE_ADD(CURDATE(), INTERVAL 1 DAY), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_todo WHERE title = '提交产品周会纪要' AND user_id = 1);

INSERT INTO sys_todo (title, user_id, priority, due_date, done)
SELECT '确认下周迭代排期', 1, 'normal', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_todo WHERE title = '确认下周迭代排期' AND user_id = 1);

INSERT INTO sys_todo (title, user_id, priority, due_date, done)
SELECT '回复 IT 工单 #1024', 1, 'high', CURDATE(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_todo WHERE title = '回复 IT 工单 #1024' AND user_id = 1);

INSERT INTO sys_todo (title, user_id, priority, due_date, done)
SELECT '更新运维手册目录', 1, 'normal', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1
WHERE NOT EXISTS (SELECT 1 FROM sys_todo WHERE title = '更新运维手册目录' AND user_id = 1);

INSERT INTO sys_todo (title, user_id, priority, due_date, done)
SELECT '审核关键词映射配置', 1, 'normal', DATE_ADD(CURDATE(), INTERVAL 2 DAY), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_todo WHERE title = '审核关键词映射配置' AND user_id = 1);

-- ========== 流程审批 ==========

INSERT INTO sys_approval_request (type, user_id, user_name, title, form_data, status)
SELECT 'leave', 2, '张三', '年假申请 3 天', '{"days":3,"startDate":"2026-05-28","reason":"家庭事务"}', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM sys_approval_request WHERE title = '年假申请 3 天' AND user_id = 2);

INSERT INTO sys_approval_request (type, user_id, user_name, title, form_data, status)
SELECT 'expense', 3, '李四', '5月差旅报销', '{"amount":1280.50,"items":"高铁+住宿"}', 'manager_approved'
WHERE NOT EXISTS (SELECT 1 FROM sys_approval_request WHERE title = '5月差旅报销' AND user_id = 3);

INSERT INTO sys_approval_request (type, user_id, user_name, title, form_data, status)
SELECT 'purchase', 4, '王五', '采购会议麦克风 x2', '{"amount":3600,"vendor":"某某科技"}', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM sys_approval_request WHERE title = '采购会议麦克风 x2' AND user_id = 4);

INSERT INTO sys_approval_request (type, user_id, user_name, title, form_data, status)
SELECT 'leave', 5, '赵六', '事假 0.5 天', '{"days":0.5,"startDate":"2026-05-26","reason":"体检"}', 'approved'
WHERE NOT EXISTS (SELECT 1 FROM sys_approval_request WHERE title = '事假 0.5 天' AND user_id = 5);
