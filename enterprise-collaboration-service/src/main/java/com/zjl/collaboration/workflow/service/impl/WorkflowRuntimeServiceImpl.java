package com.zjl.collaboration.workflow.service.impl;

import com.zjl.collaboration.entity.SysApprovalRequest;
import com.zjl.collaboration.mapper.SysApprovalRequestMapper;
import com.zjl.collaboration.workflow.entity.WfInstance;
import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfRecord;
import com.zjl.collaboration.workflow.entity.WfTask;
import com.zjl.collaboration.workflow.entity.WfTemplate;
import com.zjl.collaboration.workflow.enums.WfAction;
import com.zjl.collaboration.workflow.enums.WfBusinessType;
import com.zjl.collaboration.workflow.enums.WfInstanceStatus;
import com.zjl.collaboration.workflow.enums.WfTaskStatus;
import com.zjl.collaboration.workflow.mapper.WfInstanceMapper;
import com.zjl.collaboration.workflow.mapper.WfNodeMapper;
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.mapper.WfTaskMapper;
import com.zjl.collaboration.workflow.service.WorkflowRuntimeService;
import com.zjl.collaboration.workflow.service.WorkflowTaskService;
import com.zjl.collaboration.workflow.service.WorkflowTemplateService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRuntimeServiceImpl implements WorkflowRuntimeService {
    private final WorkflowTemplateService templateService;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfRecordMapper recordMapper;
    private final WfNodeMapper nodeMapper;
    private final WorkflowTaskService taskService;
    private final SysApprovalRequestMapper approvalRequestMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startApproval(String approvalType, Long approvalId, Long starterId) {
        WfTemplate template = templateService.requireEnabledTemplate(WfBusinessType.APPROVAL, approvalType);
        WfNode firstNode = templateService.requireFirstApprovalNode(template.getId());
        List<WfNodeApprover> approvers = templateService.requireApprovers(firstNode.getId());
        LocalDateTime now = LocalDateTime.now();

        WfInstance instance = new WfInstance();
        instance.setTemplateId(template.getId());
        instance.setBusinessType(WfBusinessType.APPROVAL);
        instance.setBusinessId(approvalId);
        instance.setStarterId(starterId);
        instance.setStatus(WfInstanceStatus.RUNNING);
        instance.setCurrentNodeId(firstNode.getId());
        instance.setStartedAt(now);
        instance.setDeleted(0);
        instanceMapper.insert(instance);

        for (WfNodeApprover approver : approvers) {
            WfTask task = new WfTask();
            task.setInstanceId(instance.getId());
            task.setNodeId(firstNode.getId());
            task.setAssigneeType(approver.getApproverType());
            task.setAssigneeId(approver.getApproverId());
            task.setStatus(WfTaskStatus.PENDING);
            task.setDeleted(0);
            taskMapper.insert(task);
        }

        WfRecord record = new WfRecord();
        record.setInstanceId(instance.getId());
        record.setNodeId(firstNode.getId());
        record.setOperatorId(starterId);
        record.setAction(WfAction.START);
        record.setToStatus(WfInstanceStatus.RUNNING);
        record.setCreatedAt(now);
        recordMapper.insert(record);

        return instance.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTask(Long taskId, Long operatorId, String comment) {
        WfTask task = requireTask(taskId);
        WfInstance instance = requireInstance(task.getInstanceId());
        WfNode currentNode = requireNode(task.getNodeId());
        LocalDateTime now = LocalDateTime.now();

        taskService.requireCanHandle(task, operatorId);
        task.setStatus(WfTaskStatus.APPROVED);
        task.setClaimedBy(operatorId);
        task.setHandledAt(now);
        task.setComment(comment);
        taskMapper.updateById(task);
        taskService.closeOtherPendingTasks(instance.getId(), currentNode.getId(), task.getId(), operatorId);

        insertRecord(instance.getId(), currentNode.getId(), task.getId(), operatorId,
                WfAction.APPROVE, WfTaskStatus.PENDING, WfTaskStatus.APPROVED, comment, now);

        WfNode nextNode = templateService.findNextApprovalNode(instance.getTemplateId(), currentNode.getSortOrder());
        if (nextNode != null) {
            createPendingTasks(instance.getId(), nextNode);
            instance.setCurrentNodeId(nextNode.getId());
            instance.setStatus(WfInstanceStatus.RUNNING);
            instanceMapper.updateById(instance);
            return;
        }

        instance.setStatus(WfInstanceStatus.APPROVED);
        instance.setEndedAt(now);
        instanceMapper.updateById(instance);
        updateBusinessApprovalStatus(instance.getBusinessId(), WfInstanceStatus.APPROVED);
        insertRecord(instance.getId(), currentNode.getId(), task.getId(), operatorId,
                WfAction.COMPLETE, WfInstanceStatus.RUNNING, WfInstanceStatus.APPROVED, null, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(Long taskId, Long operatorId, String comment) {
        WfTask task = requireTask(taskId);
        WfInstance instance = requireInstance(task.getInstanceId());
        LocalDateTime now = LocalDateTime.now();

        taskService.requireCanHandle(task, operatorId);
        task.setStatus(WfTaskStatus.REJECTED);
        task.setClaimedBy(operatorId);
        task.setHandledAt(now);
        task.setComment(comment);
        taskMapper.updateById(task);
        taskService.closeOtherPendingTasks(instance.getId(), task.getNodeId(), task.getId(), operatorId);

        instance.setStatus(WfInstanceStatus.REJECTED);
        instance.setEndedAt(now);
        instanceMapper.updateById(instance);
        updateBusinessApprovalStatus(instance.getBusinessId(), WfInstanceStatus.REJECTED);
        insertRecord(instance.getId(), task.getNodeId(), task.getId(), operatorId,
                WfAction.REJECT, WfInstanceStatus.RUNNING, WfInstanceStatus.REJECTED, comment, now);
    }

    private void updateBusinessApprovalStatus(Long approvalId, String status) {
        SysApprovalRequest approval = new SysApprovalRequest();
        approval.setId(approvalId);
        approval.setStatus(status);
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRequestMapper.updateById(approval);
    }

    private void createPendingTasks(Long instanceId, WfNode node) {
        for (WfNodeApprover approver : templateService.requireApprovers(node.getId())) {
            WfTask task = new WfTask();
            task.setInstanceId(instanceId);
            task.setNodeId(node.getId());
            task.setAssigneeType(approver.getApproverType());
            task.setAssigneeId(approver.getApproverId());
            task.setStatus(WfTaskStatus.PENDING);
            task.setDeleted(0);
            taskMapper.insert(task);
        }
    }

    private void insertRecord(Long instanceId, Long nodeId, Long taskId, Long operatorId,
                              String action, String fromStatus, String toStatus,
                              String comment, LocalDateTime now) {
        WfRecord record = new WfRecord();
        record.setInstanceId(instanceId);
        record.setNodeId(nodeId);
        record.setTaskId(taskId);
        record.setOperatorId(operatorId);
        record.setAction(action);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setComment(comment);
        record.setCreatedAt(now);
        recordMapper.insert(record);
    }

    private WfTask requireTask(Long taskId) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批任务不存在");
        }
        return task;
    }

    private WfInstance requireInstance(Long instanceId) {
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        return instance;
    }

    private WfNode requireNode(Long nodeId) {
        WfNode node = nodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程节点不存在");
        }
        return node;
    }
}
