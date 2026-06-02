# Approval Workflow Redesign

## Context

The current approval module is not a real workflow. `ApprovalController.nextStatus()` hard-codes transitions for leave and expense approvals, and `sys_approval_request.status` carries both business state and process state. This makes the logic hard to understand, hard to extend, and unsafe to develop further.

The project already has collaboration-domain modules for approvals, tasks, todos, meetings, IM, and documents. Workflow belongs in `enterprise-collaboration-service`; Gateway should only route and authorize workflow APIs. Knowledge/Agent may later call workflow APIs as a client, but workflow state must remain in the collaboration domain.

## Decision

Build a lightweight workflow kernel inside `enterprise-collaboration-service`.

First release uses built-in approval templates for leave and expense, but stores templates, nodes, approvers, instances, tasks, and records in generic workflow tables. This gives a clear MVP now and leaves room for future configuration screens.

Do not introduce Flowable, Camunda, or a BPMN engine in the first release. Do not build a visual workflow designer. Do not preserve the old approval page response contract; frontend and backend should both move to the new model.

## Scope

In scope:

- Built-in leave and expense approval templates.
- Workflow nodes with approvers configured by user or role.
- Any-one approval mode for nodes with multiple candidate approvers.
- Approve and reject actions.
- Reject ends the workflow immediately.
- My applications, my pending workflow tasks, approval detail, and template read APIs.
- Workflow records as the single audit trail.

Out of scope:

- Visual template designer.
- Conditional branches.
- Countersign where all approvers must approve.
- Return to applicant for edits.
- Transfer, add-sign, delegate, and administrator override.
- Standalone workflow microservice.
- Backward-compatible `sys_approval_record` based response contract.

## Architecture

The core boundary is:

- Approval is the business application form.
- Workflow is process execution and approval task handling.

`sys_approval_request` remains the business application table. It records who applied, what type of application it is, the submitted form data, and the final business status. It no longer decides workflow transitions.

Workflow tables own process state:

- Template and node definitions define the process shape.
- Instance records represent running or completed executions.
- Task records represent who can currently approve.
- Record entries are the audit trail.

Call chain:

```text
User submits leave or expense
-> create sys_approval_request
-> find enabled workflow template by approval type
-> create wf_instance
-> create first approval node task
-> approver approves or rejects task
-> workflow runtime moves to next node or ends instance
-> sync final status to sys_approval_request
-> write wf_record entries
```

## Data Model

### sys_approval_request

Keep this table, but narrow its responsibility to business application data.

Fields:

```text
id
type              leave / expense
user_id
user_name
title
form_data
status            PENDING / APPROVED / REJECTED / CANCELLED
workflow_instance_id
created_at
updated_at
deleted
```

`workflow_instance_id` links the business application to the runtime instance.

### Deprecated sys_approval_record

Stop writing this table. New audit records use `wf_record`.

Existing data may remain in the database until a separate cleanup or migration plan is approved.

### wf_template

Represents an approval workflow template such as leave approval or expense approval.

Fields:

```text
id
code              leave / expense
name              Leave Approval / Expense Approval
business_type     approval
enabled
created_at
updated_at
deleted
```

### wf_node

Represents a node in a template.

Fields:

```text
id
template_id
node_key          manager_approve / finance_approve
node_name
node_type         START / APPROVAL / END
sort_order
approval_mode     ANY
created_at
updated_at
deleted
```

First release supports only linear node order by `sort_order`.

### wf_node_approver

Represents candidate approvers for an approval node.

Fields:

```text
id
node_id
approver_type     USER / ROLE
approver_id       user id or role id
created_at
```

### wf_instance

Represents one running or completed workflow.

Fields:

```text
id
template_id
business_type     approval
business_id       sys_approval_request.id
starter_id
status            RUNNING / APPROVED / REJECTED / CANCELLED
current_node_id
started_at
ended_at
created_at
updated_at
deleted
```

### wf_task

Represents a pending or completed approval task.

Fields:

```text
id
instance_id
node_id
assignee_type     USER / ROLE
assignee_id
status            PENDING / APPROVED / REJECTED / CLOSED
claimed_by        actual user who handled a ROLE task
handled_at
comment
created_at
updated_at
deleted
```

For role tasks, `assignee_id` is the role id and `claimed_by` is the user id that actually approved or rejected.

### wf_record

Represents workflow audit history.

Fields:

```text
id
instance_id
node_id
task_id
operator_id
action            START / APPROVE / REJECT / AUTO_CLOSE / COMPLETE
from_status
to_status
comment
created_at
```

## Built-In Templates

Leave approval:

```text
START
-> Manager Approval
-> END
```

Expense approval:

```text
START
-> Manager Approval
-> Finance Approval
-> END
```

Approvers:

- Nodes may use USER approvers.
- Nodes may use ROLE approvers.
- First release should support both types.
- The migration seeds `manager` and `finance` roles when they do not already exist, then binds built-in approval templates to those role approvers.

## Status Flow

`sys_approval_request.status`:

```text
PENDING
APPROVED
REJECTED
CANCELLED
```

`wf_instance.status`:

```text
RUNNING
APPROVED
REJECTED
CANCELLED
```

`wf_task.status`:

```text
PENDING
APPROVED
REJECTED
CLOSED
```

Submit application:

```text
approval_request = PENDING
wf_instance = RUNNING
create first approval task
write START record
```

Approve task:

```text
current task = APPROVED
other pending tasks for same node = CLOSED
if next approval node exists:
  create next node tasks
  instance remains RUNNING
else:
  instance = APPROVED
  approval_request = APPROVED
  write COMPLETE record
```

Reject task:

```text
current task = REJECTED
other pending tasks for same node = CLOSED
instance = REJECTED
approval_request = REJECTED
write REJECT record
```

## API Design

### Create approval

```text
POST /api/approvals
```

Request:

```json
{
  "type": "leave",
  "title": "Leave request",
  "formData": {
    "startDate": "2026-06-03",
    "endDate": "2026-06-04",
    "reason": "Personal leave"
  }
}
```

Response:

```json
{
  "approvalId": 1001,
  "instanceId": 2001
}
```

### My applications

```text
GET /api/approvals/my
```

Returns applications created by the current user.

### Approval detail

```text
GET /api/approvals/{id}
```

Returns:

- application fields
- workflow instance status
- current node
- workflow records

### My workflow tasks

```text
GET /api/workflow/tasks/my
```

Returns pending tasks assigned directly to the current user and role tasks matching the current user's roles.

### Handle workflow task

```text
POST /api/workflow/tasks/{taskId}/actions
```

Request:

```json
{
  "action": "APPROVE",
  "comment": "Approved"
}
```

or:

```json
{
  "action": "REJECT",
  "comment": "Rejected because the budget is not valid"
}
```

### Workflow templates

```text
GET /api/workflow/templates
GET /api/workflow/templates/{id}
```

First release exposes template read APIs only. Template creation and editing are not included.

## Backend Structure

Controllers:

```text
ApprovalController
WorkflowTaskController
WorkflowTemplateController
```

Services:

```text
ApprovalApplicationService
WorkflowRuntimeService
WorkflowTaskService
WorkflowTemplateService
```

Responsibilities:

`ApprovalApplicationService`:

- Create approval requests.
- Query my applications.
- Query application details.
- Start workflow through `WorkflowRuntimeService`.
- Sync final workflow status back to approval request.

`WorkflowRuntimeService`:

- Resolve template by business type and approval type.
- Create workflow instance.
- Create first node tasks.
- Handle approve and reject.
- Move to the next approval node.
- End workflow.
- Write workflow records.

`WorkflowTaskService`:

- Query my pending tasks.
- Check whether the current user may handle a task.
- Close other pending tasks for the same node after one candidate handles it.

`WorkflowTemplateService`:

- Query templates.
- Load nodes and approvers.
- Validate template completeness before runtime use.

Controllers must not contain workflow transition decisions.

## Permissions

Gateway should add workflow and approval permissions:

```text
workflow:template:read
workflow:task:read
workflow:task:write
approval:read
approval:write
```

Rules:

- Creating an application requires login. All enabled users may submit leave and expense applications in the first release.
- Users can view their own applications.
- Admin users can view all applications.
- Users can view only tasks assigned to them or to one of their roles.
- Users can handle only candidate tasks they are allowed to claim.
- Admin users should not be allowed to approve on behalf of others unless a later administrator-intervention feature is designed.
- Template read APIs require administrator access or `workflow:template:read`.

## Frontend Changes

The old approval page can be rebuilt around the new model.

Views:

- My applications.
- My pending approvals.
- Approval detail with workflow timeline.
- Submit leave request.
- Submit expense request.
- Template read-only view for admins.

No backward compatibility with old `records` response is required.

## Testing Strategy

Minimum tests:

1. Start leave approval:

```text
approval_request = PENDING
wf_instance = RUNNING
first wf_task exists
START wf_record exists
```

2. Single-node approval completes:

```text
task = APPROVED
wf_instance = APPROVED
approval_request = APPROVED
APPROVE and COMPLETE records exist
```

3. Multi-node approval advances:

```text
manager approve creates finance task
finance approve completes workflow
```

4. Reject ends workflow:

```text
task = REJECTED
other same-node pending tasks = CLOSED
wf_instance = REJECTED
approval_request = REJECTED
REJECT record exists
```

5. Authorization:

```text
non-candidate user cannot handle task
role candidate user can handle role task
```

## Migration Notes

Database changes must be delivered through an incremental migration script. The full schema file should be updated only as the latest complete schema snapshot after migration is defined.

Existing `sys_approval_request` rows are left as historical legacy data and are not backfilled into workflow instances in the first release. New rows must use `workflow_instance_id` and workflow runtime tables.

`sys_approval_record` should not be used by new code.
