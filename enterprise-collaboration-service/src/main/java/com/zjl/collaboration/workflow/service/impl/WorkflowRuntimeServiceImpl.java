package com.zjl.collaboration.workflow.service.impl;

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
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.mapper.WfTaskMapper;
import com.zjl.collaboration.workflow.service.WorkflowRuntimeService;
import com.zjl.collaboration.workflow.service.WorkflowTemplateService;
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
    public void approveTask(Long taskId, Long operatorId, String comment) {
        throw new UnsupportedOperationException("approveTask will be implemented in the approve/reject runtime task");
    }

    @Override
    public void rejectTask(Long taskId, Long operatorId, String comment) {
        throw new UnsupportedOperationException("rejectTask will be implemented in the approve/reject runtime task");
    }
}
