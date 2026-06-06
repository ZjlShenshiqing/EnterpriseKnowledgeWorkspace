package com.zjl.collaboration.web;

import com.zjl.collaboration.workflow.dto.WorkflowActionRequest;
import com.zjl.collaboration.workflow.service.WorkflowRuntimeService;
import com.zjl.collaboration.workflow.service.WorkflowTaskQueryService;
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

/**
 * 工作流任务接口。
 */
@RestController
@RequestMapping("/api/workflow/tasks")
@RequiredArgsConstructor
public class WorkflowTaskController {

    private final WorkflowTaskQueryService workflowTaskQueryService;
    private final WorkflowRuntimeService runtimeService;

    @GetMapping("/my")
    public Result<List<WorkflowTaskVO>> listMine(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(workflowTaskQueryService.listMine(userId));
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
}
