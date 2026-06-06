package com.zjl.collaboration.workflow.service.impl;

import com.zjl.collaboration.entity.SysApprovalRequest;
import com.zjl.collaboration.mapper.SysApprovalRequestMapper;
import com.zjl.collaboration.workflow.entity.WfInstance;
import com.zjl.collaboration.workflow.entity.WfTask;
import com.zjl.collaboration.workflow.mapper.WfInstanceMapper;
import com.zjl.collaboration.workflow.service.WorkflowTaskQueryService;
import com.zjl.collaboration.workflow.service.WorkflowTaskService;
import com.zjl.collaboration.workflow.vo.WorkflowTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流任务查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class WorkflowTaskQueryServiceImpl implements WorkflowTaskQueryService {

    private final WorkflowTaskService taskService;
    private final WfInstanceMapper instanceMapper;
    private final SysApprovalRequestMapper approvalRequestMapper;

    @Override
    public List<WorkflowTaskVO> listMine(Long userId) {
        return taskService.listMyPendingTasks(userId).stream().map(this::toVO).toList();
    }

    private WorkflowTaskVO toVO(WfTask task) {
        WorkflowTaskVO vo = new WorkflowTaskVO();
        vo.setId(task.getId());
        vo.setInstanceId(task.getInstanceId());
        vo.setNodeId(task.getNodeId());
        vo.setAssigneeType(task.getAssigneeType());
        vo.setAssigneeId(task.getAssigneeId());
        vo.setStatus(task.getStatus());
        vo.setClaimedBy(task.getClaimedBy());
        vo.setHandledAt(task.getHandledAt());
        vo.setComment(task.getComment());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        WfInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance != null) {
            SysApprovalRequest approval = approvalRequestMapper.selectById(instance.getBusinessId());
            if (approval != null) {
                vo.setApprovalId(approval.getId());
                vo.setApprovalType(approval.getType());
                vo.setApprovalTitle(approval.getTitle());
                vo.setApplicantName(approval.getUserName());
                vo.setApprovalStatus(approval.getStatus());
            }
        }
        return vo;
    }
}
