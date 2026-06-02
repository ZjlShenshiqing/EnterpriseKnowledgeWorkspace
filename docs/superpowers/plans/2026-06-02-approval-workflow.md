# Approval Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hard-coded approval status machine with a lightweight workflow kernel for leave and expense approvals.

**Architecture:** Implement workflow runtime inside `enterprise-collaboration-service`. Keep `sys_approval_request` as the business application table, add generic `wf_*` runtime tables, expose new workflow task/template APIs, route `/api/workflow/**` through Gateway, and rebuild the approvals page around applications and workflow tasks.

**Tech Stack:** Java 17, Spring Boot 3.4.4, MyBatis-Plus, MySQL, Maven, Vue 3, Element Plus, Vite.

---

## File Structure

Backend files to create or modify:

- Modify: `enterprise-collaboration-service/pom.xml`  
  Adds test dependencies and Surefire so JUnit 5 tests run.
- Create: `enterprise-collaboration-service/src/main/resources/db/migration/010-approval-workflow.sql`  
  Adds workflow tables, alters `sys_approval_request`, seeds roles and built-in templates.
- Modify: `resouces/enterprise_knowledge_workspace.sql`  
  Updates the complete schema snapshot after the incremental migration exists.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfBusinessType.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfNodeType.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfInstanceStatus.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfTaskStatus.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfAction.java`  
  Workflow status/action/type constants.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfTemplate.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfNode.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfNodeApprover.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfInstance.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfTask.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfRecord.java`  
  MyBatis-Plus entities for `wf_template`, `wf_node`, `wf_node_approver`, `wf_instance`, `wf_task`, `wf_record`.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfTemplateMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfNodeMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfNodeApproverMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfInstanceMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfTaskMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfRecordMapper.java`  
  Mapper interfaces.
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysApprovalRequest.java`  
  Adds `workflowInstanceId` and `deleted`.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/dto/ApprovalCreateRequest.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/dto/WorkflowActionRequest.java`  
  Request DTOs for approval creation and workflow actions.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/ApprovalCreateVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/ApprovalListVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/ApprovalDetailVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/WfRecordVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/WorkflowTaskVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/WorkflowTemplateVO.java`  
  Response VOs for approval list/detail, task list, template detail.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/ApprovalApplicationService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowTaskService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowTemplateService.java`  
  Service interfaces.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/ApprovalApplicationServiceImpl.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowRuntimeServiceImpl.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowTaskServiceImpl.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowTemplateServiceImpl.java`  
  Runtime implementation.
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ApprovalController.java`  
  Removes hard-coded `nextStatus()` and delegates to the application service.
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/WorkflowTaskController.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/WorkflowTemplateController.java`
- Create: `enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeServiceImplTest.java`
- Create: `enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service/WorkflowTaskServiceImplTest.java`
- Modify: `enterprise-gateway-service/src/main/resources/application.yml`
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java`
- Modify: `resouces/enterprise_knowledge_workspace.sql`

Frontend files:

- Modify: `enterprise-web/src/pages/Approvals.vue`  
  Rebuilds the page around My Applications, My Pending Approvals, and workflow timeline detail.
- Modify: `enterprise-web/src/api/index.js`  
  Adds approval/workflow API wrappers used by `Approvals.vue`.

---

### Task 1: Test Infrastructure

**Files:**
- Modify: `enterprise-collaboration-service/pom.xml`

- [ ] **Step 1: Add test dependencies and Surefire**

Add these dependencies before `</dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Replace the existing single-line build block with:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>${spring.boot.version}</version>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
        </plugin>
    </plugins>
</build>
```

- [ ] **Step 2: Verify collaboration tests can run with no tests**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`. If unrelated modules fail because they have no tests, keep `-DfailIfNoTests=false`.

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/pom.xml
git commit -m "test: 配置协同服务单元测试"
```

---

### Task 2: Workflow Schema and Entities

**Files:**
- Create: `enterprise-collaboration-service/src/main/resources/db/migration/010-approval-workflow.sql`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysApprovalRequest.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfTemplate.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfNode.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfNodeApprover.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfInstance.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfTask.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/entity/WfRecord.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfTemplateMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfNodeMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfNodeApproverMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfInstanceMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfTaskMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/mapper/WfRecordMapper.java`

- [ ] **Step 1: Create migration**

Create `010-approval-workflow.sql` with:

```sql
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

INSERT INTO sys_role (code, name)
SELECT 'manager', '部门主管'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'manager');

INSERT INTO sys_role (code, name)
SELECT 'finance', '财务'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'finance');

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
JOIN sys_role r ON r.code = 'manager'
WHERE n.node_key = 'manager_approve'
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_approver a
      WHERE a.node_id = n.id AND a.approver_type = 'ROLE' AND a.approver_id = r.id
  );

INSERT INTO wf_node_approver (node_id, approver_type, approver_id)
SELECT n.id, 'ROLE', r.id
FROM wf_node n
JOIN wf_template t ON n.template_id = t.id
JOIN sys_role r ON r.code = 'finance'
WHERE t.code = 'expense'
  AND n.node_key = 'finance_approve'
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_approver a
      WHERE a.node_id = n.id AND a.approver_type = 'ROLE' AND a.approver_id = r.id
  );
```

- [ ] **Step 2: Update `SysApprovalRequest`**

Use this final class:

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_approval_request")
public class SysApprovalRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private Long userId;
    private String userName;
    private String title;
    private String formData;
    private String status;
    private Long workflowInstanceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
```

- [ ] **Step 3: Create workflow entities**

Create each entity with `@TableName`, Lombok `@Data`, `@TableId(type = IdType.AUTO)`, and fields matching the migration exactly. Use these field sets:

```text
WfTemplate:
id, code, name, businessType, enabled, createdAt, updatedAt, deleted

WfNode:
id, templateId, nodeKey, nodeName, nodeType, sortOrder, approvalMode, createdAt, updatedAt, deleted

WfNodeApprover:
id, nodeId, approverType, approverId, createdAt

WfInstance:
id, templateId, businessType, businessId, starterId, status, currentNodeId, startedAt, endedAt, createdAt, updatedAt, deleted

WfTask:
id, instanceId, nodeId, assigneeType, assigneeId, status, claimedBy, handledAt, comment, createdAt, updatedAt, deleted

WfRecord:
id, instanceId, nodeId, taskId, operatorId, action, fromStatus, toStatus, comment, createdAt
```

Example `WfTask`:

```java
package com.zjl.collaboration.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_task")
public class WfTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long nodeId;
    private String assigneeType;
    private Long assigneeId;
    private String status;
    private Long claimedBy;
    private LocalDateTime handledAt;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
```

- [ ] **Step 4: Create workflow mappers**

Create these exact mapper interfaces. Each mapper extends `BaseMapper<T>` and has `@Mapper`.

```text
WfTemplateMapper extends BaseMapper<WfTemplate>
WfNodeMapper extends BaseMapper<WfNode>
WfNodeApproverMapper extends BaseMapper<WfNodeApprover>
WfInstanceMapper extends BaseMapper<WfInstance>
WfTaskMapper extends BaseMapper<WfTask>
WfRecordMapper extends BaseMapper<WfRecord>
```

Example:

```java
package com.zjl.collaboration.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {
}
```

- [ ] **Step 5: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add enterprise-collaboration-service/src/main/resources/db/migration/010-approval-workflow.sql \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysApprovalRequest.java \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow
git commit -m "feat: 添加审批工作流数据模型"
```

---

### Task 3: Workflow Constants and Template Service

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfBusinessType.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfNodeType.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfInstanceStatus.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfTaskStatus.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums/WfAction.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowTemplateService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowTemplateServiceImpl.java`

- [ ] **Step 1: Create constants**

Use simple final classes or enums. Example:

```java
package com.zjl.collaboration.workflow.enums;

public final class WfTaskStatus {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CLOSED = "CLOSED";

    private WfTaskStatus() {
    }
}
```

Create these constants:

```text
WfBusinessType.APPROVAL = "approval"
WfNodeType.START = "START"
WfNodeType.APPROVAL = "APPROVAL"
WfNodeType.END = "END"
WfInstanceStatus.RUNNING = "RUNNING"
WfInstanceStatus.APPROVED = "APPROVED"
WfInstanceStatus.REJECTED = "REJECTED"
WfInstanceStatus.CANCELLED = "CANCELLED"
WfTaskStatus.PENDING = "PENDING"
WfTaskStatus.APPROVED = "APPROVED"
WfTaskStatus.REJECTED = "REJECTED"
WfTaskStatus.CLOSED = "CLOSED"
WfAction.START = "START"
WfAction.APPROVE = "APPROVE"
WfAction.REJECT = "REJECT"
WfAction.AUTO_CLOSE = "AUTO_CLOSE"
WfAction.COMPLETE = "COMPLETE"
```

- [ ] **Step 2: Create service interface**

```java
package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfTemplate;

import java.util.List;

public interface WorkflowTemplateService {
    WfTemplate requireEnabledTemplate(String businessType, String code);
    List<WfNode> listNodes(Long templateId);
    WfNode requireFirstApprovalNode(Long templateId);
    WfNode findNextApprovalNode(Long templateId, Integer currentSortOrder);
    List<WfNodeApprover> requireApprovers(Long nodeId);
}
```

- [ ] **Step 3: Implement service**

Implementation rules:

```java
requireEnabledTemplate:
  select one by businessType, code, enabled=1, deleted=0
  throw BizException(ErrorCode.PARAM_INVALID, "流程模板不存在或未启用")

listNodes:
  select by templateId, deleted=0, order by sortOrder asc

requireFirstApprovalNode:
  first node where nodeType == APPROVAL
  throw BizException(ErrorCode.PARAM_INVALID, "流程模板缺少审批节点")

findNextApprovalNode:
  first node where sortOrder > currentSortOrder and nodeType == APPROVAL
  return null when no next approval node exists

requireApprovers:
  select by nodeId
  throw BizException(ErrorCode.PARAM_INVALID, "审批节点缺少审批人")
```

- [ ] **Step 4: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/enums \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowTemplateService.java \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowTemplateServiceImpl.java
git commit -m "feat: 添加工作流模板服务"
```

---

### Task 4: Start Workflow Runtime

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowRuntimeServiceImpl.java`
- Create: `enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeServiceImplTest.java`

- [ ] **Step 1: Write failing test for starting workflow**

Test name:

```java
@Test
void startApprovalCreatesRunningInstanceFirstTasksAndStartRecord()
```

Arrange:

```text
template id=10 code=leave
first approval node id=20
approvers: ROLE id=2
business id=1001
starter id=6
```

Assert:

```text
inserted WfInstance.status == RUNNING
inserted WfTask.status == PENDING
inserted WfTask.assigneeType == ROLE
inserted WfRecord.action == START
returned instance id is the inserted instance id
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -Dtest=WorkflowRuntimeServiceImplTest -DfailIfNoTests=false
```

Expected: fail because `WorkflowRuntimeServiceImpl` does not exist or `startApproval` is not implemented.

- [ ] **Step 3: Define service interface**

```java
package com.zjl.collaboration.workflow.service;

public interface WorkflowRuntimeService {
    Long startApproval(String approvalType, Long approvalId, Long starterId);
    void approveTask(Long taskId, Long operatorId, String comment);
    void rejectTask(Long taskId, Long operatorId, String comment);
}
```

- [ ] **Step 4: Implement `startApproval`**

Implementation algorithm:

```text
template = templateService.requireEnabledTemplate("approval", approvalType)
firstNode = templateService.requireFirstApprovalNode(template.id)
approvers = templateService.requireApprovers(firstNode.id)
insert wf_instance RUNNING currentNodeId=firstNode.id
for each approver:
  insert wf_task PENDING assigneeType/assigneeId from approver
insert wf_record START
return instance.id
```

- [ ] **Step 5: Run test and verify it passes**

Run the same Maven command.

Expected: one test runs and passes.

- [ ] **Step 6: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeService.java \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowRuntimeServiceImpl.java \
  enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeServiceImplTest.java
git commit -m "feat: 实现审批工作流启动"
```

---

### Task 5: Approve and Reject Runtime

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowRuntimeServiceImpl.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/WorkflowTaskService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/WorkflowTaskServiceImpl.java`
- Modify: `enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service/WorkflowRuntimeServiceImplTest.java`
- Create: `enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service/WorkflowTaskServiceImplTest.java`

- [ ] **Step 1: Write failing tests**

Add tests:

```java
@Test
void approveTaskCreatesNextNodeTaskWhenNextApprovalNodeExists()

@Test
void approveTaskCompletesWorkflowWhenNoNextApprovalNodeExists()

@Test
void rejectTaskEndsWorkflowAndClosesSameNodePendingTasks()

@Test
void nonCandidateCannotHandleTask()

@Test
void roleCandidateCanHandleRoleTask()
```

Expected assertions:

```text
APPROVE with next node:
  current task APPROVED
  sibling task CLOSED
  next node task PENDING
  instance RUNNING

APPROVE final node:
  current task APPROVED
  instance APPROVED
  COMPLETE record exists

REJECT:
  current task REJECTED
  sibling task CLOSED
  instance REJECTED
  REJECT record exists

Authorization:
  direct USER task can only be handled by assignee
  ROLE task can be handled by user with matching role
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -Dtest=WorkflowRuntimeServiceImplTest,WorkflowTaskServiceImplTest -DfailIfNoTests=false
```

Expected: fail because approve/reject/authorization are incomplete.

- [ ] **Step 3: Implement task authorization**

`WorkflowTaskService` interface:

```java
package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.entity.WfTask;

import java.util.List;

public interface WorkflowTaskService {
    List<WfTask> listMyPendingTasks(Long userId);
    void requireCanHandle(WfTask task, Long userId);
    void closeOtherPendingTasks(Long instanceId, Long nodeId, Long handledTaskId, Long operatorId);
}
```

Implementation:

```text
USER task:
  task.assigneeId must equal userId

ROLE task:
  use GatewayUserClient.getById(userId)
  read user.roles
  allow if any role id or role code matches task.assigneeId

closeOtherPendingTasks:
  find PENDING tasks same instanceId/nodeId excluding handledTaskId
  set CLOSED, claimedBy=operatorId, handledAt=now
  write AUTO_CLOSE record per task
```

- [ ] **Step 4: Implement approve**

Algorithm:

```text
load task and instance
taskService.requireCanHandle(task, operatorId)
set task APPROVED, claimedBy=operatorId, handledAt=now, comment
close sibling pending tasks
write APPROVE record
currentNode = task.node
nextNode = templateService.findNextApprovalNode(instance.templateId, currentNode.sortOrder)
if nextNode exists:
  create tasks for nextNode approvers
  instance.currentNodeId = nextNode.id
else:
  instance.status = APPROVED
  instance.endedAt = now
  write COMPLETE record
```

- [ ] **Step 5: Implement reject**

Algorithm:

```text
load task and instance
taskService.requireCanHandle(task, operatorId)
set task REJECTED, claimedBy=operatorId, handledAt=now, comment
close sibling pending tasks
instance.status = REJECTED
instance.endedAt = now
write REJECT record
```

- [ ] **Step 6: Run tests and verify pass**

Run the same Maven command.

Expected: all workflow runtime/task tests pass.

- [ ] **Step 7: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service \
  enterprise-collaboration-service/src/test/java/com/zjl/collaboration/workflow/service
git commit -m "feat: 实现审批工作流审批流转"
```

---

### Task 6: Approval Application APIs

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ApprovalController.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/dto/ApprovalCreateRequest.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/dto/WorkflowActionRequest.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/ApprovalCreateVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/ApprovalListVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/ApprovalDetailVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/WfRecordVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/ApprovalApplicationService.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service/impl/ApprovalApplicationServiceImpl.java`

- [ ] **Step 1: Write failing controller/service tests**

Create tests for:

```text
create approval calls WorkflowRuntimeService.startApproval and stores workflowInstanceId
my approvals returns only current user's records
detail returns application + instance + records
```

- [ ] **Step 2: Create DTOs**

`ApprovalCreateRequest`:

```java
package com.zjl.collaboration.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ApprovalCreateRequest {
    private String type;
    private String title;
    private Map<String, Object> formData;
}
```

`WorkflowActionRequest`:

```java
package com.zjl.collaboration.workflow.dto;

import lombok.Data;

@Data
public class WorkflowActionRequest {
    private String action;
    private String comment;
}
```

- [ ] **Step 3: Implement application service**

Methods:

```java
ApprovalCreateVO create(ApprovalCreateRequest request, Long userId);
List<ApprovalListVO> listMine(Long userId);
List<ApprovalListVO> listAll();
ApprovalDetailVO detail(Long approvalId, Long userId, boolean admin);
```

Rules:

```text
create:
  validate type is leave or expense
  title not blank
  serialize formData to JSON using ObjectMapper
  insert sys_approval_request PENDING
  start workflow
  update workflowInstanceId
  return approvalId + instanceId

detail:
  user can view own application
  admin can view any application
  include wf_instance and wf_record rows
```

- [ ] **Step 4: Replace `ApprovalController`**

Expose:

```text
POST /api/approvals
GET /api/approvals/my
GET /api/approvals
GET /api/approvals/{id}
```

Remove:

```text
nextStatus()
POST /api/approvals/{id}/approve
direct writes to sys_approval_record
```

- [ ] **Step 5: Run tests**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ApprovalController.java \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/dto \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/service \
  enterprise-collaboration-service/src/test/java
git commit -m "feat: 重构审批申请接口"
```

---

### Task 7: Workflow Task and Template APIs

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/WorkflowTaskController.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/WorkflowTemplateController.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/WorkflowTaskVO.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo/WorkflowTemplateVO.java`

- [ ] **Step 1: Implement task controller**

Endpoints:

```text
GET /api/workflow/tasks/my
POST /api/workflow/tasks/{taskId}/actions
```

Action handling:

```java
if ("APPROVE".equals(request.getAction())) {
    workflowRuntimeService.approveTask(taskId, userId, request.getComment());
} else if ("REJECT".equals(request.getAction())) {
    workflowRuntimeService.rejectTask(taskId, userId, request.getComment());
} else {
    throw new BizException(ErrorCode.PARAM_INVALID, "不支持的审批动作");
}
```

- [ ] **Step 2: Implement template controller**

Endpoints:

```text
GET /api/workflow/templates
GET /api/workflow/templates/{id}
```

Return template, nodes, and approver rows.

- [ ] **Step 3: Run tests**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/WorkflowTaskController.java \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/WorkflowTemplateController.java \
  enterprise-collaboration-service/src/main/java/com/zjl/collaboration/workflow/vo
git commit -m "feat: 添加工作流任务和模板接口"
```

---

### Task 8: Gateway Routing and Permissions

**Files:**
- Modify: `enterprise-gateway-service/src/main/resources/application.yml`
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java`
- Modify: `resouces/enterprise_knowledge_workspace.sql`

- [ ] **Step 1: Add Gateway route**

In the collaboration route path list, add:

```yaml
/api/workflow/**
```

Final predicate should include:

```yaml
- Path=/api/meetings/**,/api/todos/**,/api/tasks/**,/api/notifications/**,/api/chat/**,/api/docs/**,/api/approvals/**,/api/workflow/**,/api/announcements/**,/api/intents/**,/api/keyword-mappings/**
```

- [ ] **Step 2: Add permissions in `SaTokenConfig`**

Rules:

```text
GET /api/workflow/tasks/** -> workflow:task:read
POST /api/workflow/tasks/** -> workflow:task:write
GET /api/workflow/templates/** -> workflow:template:read
POST /api/approvals -> login only
GET /api/approvals/my -> login only
GET /api/approvals/** -> approval:read
```

Implement with `SaRouter.match` next to existing approval rules.

- [ ] **Step 3: Add permission seed rows to full schema**

Add permissions:

```sql
('workflow:template:read', '查看工作流模板'),
('workflow:task:read', '查看工作流任务'),
('workflow:task:write', '处理工作流任务'),
('approval:read', '查看审批'),
('approval:write', '发起审批')
```

Bind them to admin role in the same style as existing permission seed data.

- [ ] **Step 4: Run gateway tests**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-gateway-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add enterprise-gateway-service/src/main/resources/application.yml \
  enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java \
  resouces/enterprise_knowledge_workspace.sql
git commit -m "feat: 添加工作流网关权限"
```

---

### Task 9: Frontend Approval Page Rewrite

**Files:**
- Modify: `enterprise-web/src/api/index.js`
- Modify: `enterprise-web/src/pages/Approvals.vue`

- [ ] **Step 1: Add API wrappers**

Add to `enterprise-web/src/api/index.js`:

```js
export function createApproval(body) {
  return fetch('/api/approvals', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify(body)
  }).then(r => r.json())
}

export function getMyApprovals() {
  return fetch('/api/approvals/my', { headers: getAuthHeaders() }).then(r => r.json())
}

export function getApprovalDetail(id) {
  return fetch(`/api/approvals/${id}`, { headers: getAuthHeaders() }).then(r => r.json())
}

export function getMyWorkflowTasks() {
  return fetch('/api/workflow/tasks/my', { headers: getAuthHeaders() }).then(r => r.json())
}

export function handleWorkflowTask(taskId, action, comment) {
  return fetch(`/api/workflow/tasks/${taskId}/actions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ action, comment })
  }).then(r => r.json())
}
```

- [ ] **Step 2: Rebuild tabs in `Approvals.vue`**

Use tabs:

```text
我的申请
我的待审
```

Remove admin-only pending logic. My pending approvals should come from `/api/workflow/tasks/my`, not `/api/approvals`.

- [ ] **Step 3: Update action buttons**

Approve:

```js
await handleWorkflowTask(task.id, 'APPROVE', '')
```

Reject:

```js
await handleWorkflowTask(task.id, 'REJECT', value || '')
```

- [ ] **Step 4: Update detail dialog**

Render:

```text
approval title/type/status/formData
workflow instance status/current node
wf_record timeline
```

Do not read `detail.records` from old `sys_approval_record` response.

- [ ] **Step 5: Build frontend**

Run:

```bash
npm run build
```

from `enterprise-web`.

Expected: `✓ built`.

- [ ] **Step 6: Commit**

```bash
git add enterprise-web/src/api/index.js enterprise-web/src/pages/Approvals.vue
git commit -m "feat: 重构审批工作流页面"
```

---

### Task 10: Final Verification and Documentation

**Files:**
- Modify: `docs/collaboration-service-code-analysis.md`
- Modify: `docs/database.md`

- [ ] **Step 1: Update docs**

Document:

```text
approval module is now split into business request + workflow runtime
old sys_approval_record is deprecated
new APIs under /api/workflow/**
new workflow tables and statuses
```

- [ ] **Step 2: Run backend tests**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-collaboration-service,enterprise-gateway-service -am -DfailIfNoTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run frontend build**

Run:

```bash
npm run build
```

from `enterprise-web`.

Expected: `✓ built`.

- [ ] **Step 4: Manual API smoke test with services running**

After MySQL migration, Gateway, Collaboration, and Platform Admin are running, use an authenticated token and run:

```bash
curl -s -X POST 'http://localhost:8098/api/approvals' \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"type":"leave","title":"请假测试","formData":{"startDate":"2026-06-03","endDate":"2026-06-04","reason":"测试"}}'
```

Expected: response `code=200`, `data.approvalId` present, `data.instanceId` present.

Then run:

```bash
curl -s 'http://localhost:8098/api/workflow/tasks/my' -H "Authorization: $TOKEN"
```

Expected: response `code=200`, pending tasks visible for a manager or finance user.

- [ ] **Step 5: Commit docs**

```bash
git add docs/collaboration-service-code-analysis.md docs/database.md
git commit -m "docs: 更新审批工作流文档"
```

---

## Self-Review Notes

Spec coverage:

- Built-in leave and expense templates: Task 2.
- Generic workflow tables: Task 2.
- USER and ROLE approvers: Tasks 2, 5.
- Any-one approval: Task 5 closes sibling tasks.
- Approve/reject and reject ends workflow: Task 5.
- My applications, my tasks, detail, template APIs: Tasks 6 and 7.
- No backward compatibility with old approval page: Task 9.
- Gateway route and permissions: Task 8.
- Testing strategy: Tasks 4, 5, 6, 10.

Placeholder scan: no deferred implementation placeholders are intended; each task names concrete files, commands, and expected behavior.

Type consistency:

- Workflow statuses use uppercase constants.
- Approval business type uses lowercase `approval` to match the design.
- Approval request `type` uses lowercase `leave` / `expense`.
