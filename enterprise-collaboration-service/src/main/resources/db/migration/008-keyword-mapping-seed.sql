-- 关键词映射示例数据（可重复执行，已存在则跳过）
-- 用法: mysql -u root -p enterprise_collaboration < 008-keyword-mapping-seed.sql

USE enterprise_collaboration;

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '报销', '财务制度知识库', 100, '优先检索报销流程、发票规范与审批节点', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '报销' AND kb_name = '财务制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '差旅', '财务制度知识库', 90, '返回差旅标准、交通住宿限额说明', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '差旅' AND kb_name = '财务制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '发票', '财务制度知识库', 85, '优先返回发票开具、验真与归档要求', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '发票' AND kb_name = '财务制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '请假', '人事制度知识库', 100, '返回请假类型、审批链与剩余假期规则', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '请假' AND kb_name = '人事制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '年假', '人事制度知识库', 95, '优先展示年假计算方式与申请入口', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '年假' AND kb_name = '人事制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '加班', '人事制度知识库', 90, '返回加班申请、调休与补贴政策', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '加班' AND kb_name = '人事制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '考勤', '人事制度知识库', 80, '优先检索打卡规则与异常处理说明', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '考勤' AND kb_name = '人事制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '会议', '协作流程知识库', 100, '返回会议室预约、冲突检测与取消流程', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '会议' AND kb_name = '协作流程知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '会议室', '协作流程知识库', 95, '优先展示会议室容量、设备与预约规范', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '会议室' AND kb_name = '协作流程知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT 'Zoom', '协作流程知识库', 85, '返回线上会议创建与入会链接说明', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = 'Zoom' AND kb_name = '协作流程知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT 'VPN', 'IT 运维知识库', 100, '优先返回 VPN 申请、安装与故障排查', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = 'VPN' AND kb_name = 'IT 运维知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '密码', 'IT 运维知识库', 95, '返回账号密码重置流程与安全要求', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '密码' AND kb_name = 'IT 运维知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '邮箱', 'IT 运维知识库', 80, '优先展示企业邮箱配置与常见问题', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '邮箱' AND kb_name = 'IT 运维知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '合同', '法务合规知识库', 100, '返回合同审批、模板与归档规范', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '合同' AND kb_name = '法务合规知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '采购', '采购管理知识库', 90, '优先检索采购申请、比价与验收流程', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '采购' AND kb_name = '采购管理知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '入职', '人事制度知识库', 85, '返回新员工 onboarding 清单与材料要求', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '入职' AND kb_name = '人事制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '离职', '人事制度知识库', 85, '返回离职交接、账号回收与证明开具流程', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '离职' AND kb_name = '人事制度知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '安全', '信息安全知识库', 100, '优先展示数据分级、脱敏与违规上报指引', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '安全' AND kb_name = '信息安全知识库');

INSERT INTO kb_keyword_mapping (keyword, kb_name, priority, strategy, enabled)
SELECT '知识库', '平台使用手册', 70, '返回文档上传、检索与权限配置说明', 1
WHERE NOT EXISTS (SELECT 1 FROM kb_keyword_mapping WHERE keyword = '知识库' AND kb_name = '平台使用手册');
