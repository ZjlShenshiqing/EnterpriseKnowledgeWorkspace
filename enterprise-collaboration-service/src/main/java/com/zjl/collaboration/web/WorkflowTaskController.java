package com.zjl.collaboration.web;

import com.zjl.collaboration.workflow.dto.WorkflowActionRequest;
import com.zjl.collaboration.workflow.entity.WfTask;
import com.zjl.collaboration.workflow.service.WorkflowRuntimeService;
import com.zjl.collaboration.workflow.service.WorkflowTaskService;
import com.zjl.collaboration.workflow.vo.WorkflowTaskVO;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflow/tasks")
@RequiredArgsConstructor
public class WorkflowTaskController {
    private final WorkflowTaskService taskService;
    private final WorkflowRuntimeService runtimeService;

    @GetMapping("/my")
    public Result<List<WorkflowTaskVO>> listMine(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(taskService.listMyPendingTasks(userId).stream().map(this::toVO).toList());
    }

    @PostMapping("/{taskId}/actions")
    public Result<Void> handle(@PathVariable Long taskId,
                               @RequestBody WorkflowActionRequest request,
                               @RequestHeader("X-User-Id") Long userId) {
        if (request != null && "APPROVE".equals(request.getAction())) {
            runtimeService.approveTask(taskId, userId, request.getComment());
            return Results.success();
        }
        if (request != null && "REJECT".equals(request.getAction())) {
            runtimeService.rejectTask(taskId, userId, request.getComment());
            return Results.success();
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "不支持的审批动作");
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
        return vo;
    }
}
