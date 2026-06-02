USE enterprise_collaboration;

ALTER TABLE sys_approval_request
    ADD COLUMN workflow_instance_id BIGINT NULL COMMENT '工作流实例ID' AFTER status,
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除' AFTER updated_at,
    ADD KEY idx_approval_workflow_instance (workflow_instance_id),
    ADD KEY idx_approval_deleted (deleted);

CREATE TABLE IF NOT EXISTS wf_template (
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

CREATE TABLE IF NOT EXISTS wf_node (
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

CREATE TABLE IF NOT EXISTS wf_node_approver (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '节点ID',
    approver_type VARCHAR(32) NOT NULL COMMENT 'USER/ROLE',
    approver_id BIGINT NOT NULL COMMENT '用户ID或角色ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wf_node_approver_node (node_id),
    KEY idx_wf_node_approver_target (approver_type, approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点审批人表';

CREATE TABLE IF NOT EXISTS wf_instance (
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

CREATE TABLE IF NOT EXISTS wf_task (
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

CREATE TABLE IF NOT EXISTS wf_record (
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

INSERT INTO enterprise_gateway.sys_role (code, name)
SELECT 'manager', '部门主管'
WHERE NOT EXISTS (SELECT 1 FROM enterprise_gateway.sys_role WHERE code = 'manager');

INSERT INTO enterprise_gateway.sys_role (code, name)
SELECT 'finance', '财务'
WHERE NOT EXISTS (SELECT 1 FROM enterprise_gateway.sys_role WHERE code = 'finance');

INSERT INTO wf_template (code, name, business_type, enabled)
SELECT 'leave', '请假审批', 'approval', 1
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE code = 'leave' AND business_type = 'approval');

INSERT INTO wf_template (code, name, business_type, enabled)
SELECT 'expense', '报销审批', 'approval', 1
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE code = 'expense' AND business_type = 'approval');

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'start', '开始', 'START', 0, 'ANY'
FROM wf_template t
WHERE t.code IN ('leave', 'expense')
  AND NOT EXISTS (SELECT 1 FROM wf_node n WHERE n.template_id = t.id AND n.node_key = 'start');

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'manager_approve', '主管审批', 'APPROVAL', 10, 'ANY'
FROM wf_template t
WHERE t.code IN ('leave', 'expense')
  AND NOT EXISTS (SELECT 1 FROM wf_node n WHERE n.template_id = t.id AND n.node_key = 'manager_approve');

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'finance_approve', '财务审批', 'APPROVAL', 20, 'ANY'
FROM wf_template t
WHERE t.code = 'expense'
  AND NOT EXISTS (SELECT 1 FROM wf_node n WHERE n.template_id = t.id AND n.node_key = 'finance_approve');

INSERT INTO wf_node (template_id, node_key, node_name, node_type, sort_order, approval_mode)
SELECT t.id, 'end', '结束', 'END',
       CASE WHEN t.code = 'expense' THEN 30 ELSE 20 END,
       'ANY'
FROM wf_template t
WHERE t.code IN ('leave', 'expense')
  AND NOT EXISTS (SELECT 1 FROM wf_node n WHERE n.template_id = t.id AND n.node_key = 'end');

INSERT INTO wf_node_approver (node_id, approver_type, approver_id)
SELECT n.id, 'ROLE', r.id
FROM wf_node n
JOIN wf_template t ON n.template_id = t.id
JOIN enterprise_gateway.sys_role r ON r.code = 'manager'
WHERE n.node_key = 'manager_approve'
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_approver a
      WHERE a.node_id = n.id AND a.approver_type = 'ROLE' AND a.approver_id = r.id
  );

INSERT INTO wf_node_approver (node_id, approver_type, approver_id)
SELECT n.id, 'ROLE', r.id
FROM wf_node n
JOIN wf_template t ON n.template_id = t.id
JOIN enterprise_gateway.sys_role r ON r.code = 'finance'
WHERE t.code = 'expense'
  AND n.node_key = 'finance_approve'
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_approver a
      WHERE a.node_id = n.id AND a.approver_type = 'ROLE' AND a.approver_id = r.id
  );
