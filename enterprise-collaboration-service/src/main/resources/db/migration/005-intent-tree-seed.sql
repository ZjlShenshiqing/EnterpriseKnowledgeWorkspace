-- 意图树示例数据（可重复执行，已存在则跳过）
-- 用法: mysql -u root -p enterprise_collaboration < 005-intent-tree-seed.sql

USE enterprise_collaboration;

-- ========== 场景（根节点） ==========

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT NULL, '会议预约', 1, 0, '会议室预约、取消、查询等协作场景', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_intent_node WHERE name = '会议预约' AND parent_id IS NULL);

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT NULL, '人事行政', 1, 1, '请假、加班、考勤等人事相关场景', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_intent_node WHERE name = '人事行政' AND parent_id IS NULL);

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT NULL, '财务报销', 1, 2, '费用报销、发票、审批进度查询', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_intent_node WHERE name = '财务报销' AND parent_id IS NULL);

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT NULL, 'IT 服务', 1, 3, '账号、VPN、设备报修等 IT 支持场景', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_intent_node WHERE name = 'IT 服务' AND parent_id IS NULL);

-- ========== 会议预约 · 子意图 ==========

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '创建会议', 2, 0, '预约会议室、安排线上会议', 1
FROM kb_intent_node p
WHERE p.name = '会议预约' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '创建会议');

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '取消会议', 2, 1, '取消已预约的会议', 1
FROM kb_intent_node p
WHERE p.name = '会议预约' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '取消会议');

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '查询我的会议', 2, 2, '查看今日/本周会议安排', 1
FROM kb_intent_node p
WHERE p.name = '会议预约' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '查询我的会议');

-- ========== 人事行政 · 子意图 ==========

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '请假申请', 2, 0, '年假、事假、病假、调休', 1
FROM kb_intent_node p
WHERE p.name = '人事行政' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '请假申请');

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '加班申请', 2, 1, '加班登记与审批', 1
FROM kb_intent_node p
WHERE p.name = '人事行政' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '加班申请');

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '考勤查询', 2, 2, '打卡记录、迟到早退统计', 1
FROM kb_intent_node p
WHERE p.name = '人事行政' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '考勤查询');

-- ========== 财务报销 · 子意图 ==========

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '提交报销', 2, 0, '差旅、餐饮、办公用品等费用报销', 1
FROM kb_intent_node p
WHERE p.name = '财务报销' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '提交报销');

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '报销进度', 2, 1, '查询报销单审批状态', 1
FROM kb_intent_node p
WHERE p.name = '财务报销' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '报销进度');

-- ========== IT 服务 · 子意图 ==========

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, '重置密码', 2, 0, '邮箱、OA、VPN 密码重置', 1
FROM kb_intent_node p
WHERE p.name = 'IT 服务' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = '重置密码');

INSERT INTO kb_intent_node (parent_id, name, level, sort_order, description, enabled)
SELECT p.id, 'VPN 申请', 2, 1, '远程办公 VPN 开通', 1
FROM kb_intent_node p
WHERE p.name = 'IT 服务' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM kb_intent_node c WHERE c.parent_id = p.id AND c.name = 'VPN 申请');

-- ========== 匹配规则（关键词 + 正则示例） ==========

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '预约会议', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '会议预约' AND n.name = '创建会议'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '预约会议');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '订会议室', 1.5, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '会议预约' AND n.name = '创建会议'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '订会议室');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'regex', '.*(取消|删掉).*(会议|例会).*', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '会议预约' AND n.name = '取消会议'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.rule_type = 'regex' AND r.expression LIKE '%取消%');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '我的会议', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '会议预约' AND n.name = '查询我的会议'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '我的会议');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '今天有什么会', 1.5, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '会议预约' AND n.name = '查询我的会议'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '今天有什么会');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '请假', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '人事行政' AND n.name = '请假申请'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '请假');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '年假', 1.5, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '人事行政' AND n.name = '请假申请'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '年假');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '加班', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '人事行政' AND n.name = '加班申请'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '加班');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '报销', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '财务报销' AND n.name = '提交报销'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '报销');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '审批到哪了', 1.5, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = '财务报销' AND n.name = '报销进度'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '审批到哪了');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', '重置密码', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = 'IT 服务' AND n.name = '重置密码'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = '重置密码');

INSERT INTO kb_intent_rule (node_id, rule_type, expression, weight, enabled)
SELECT n.id, 'keyword', 'VPN', 2.0, 1
FROM kb_intent_node n
JOIN kb_intent_node p ON n.parent_id = p.id
WHERE p.name = 'IT 服务' AND n.name = 'VPN 申请'
  AND NOT EXISTS (SELECT 1 FROM kb_intent_rule r WHERE r.node_id = n.id AND r.expression = 'VPN');
