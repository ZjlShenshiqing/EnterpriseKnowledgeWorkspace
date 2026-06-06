package com.zjl.collaboration.web;

import com.zjl.collaboration.workflow.service.WorkflowTemplateQueryService;
import com.zjl.collaboration.workflow.vo.WorkflowTemplateVO;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作流模板接口。
 */
@RestController
@RequestMapping("/api/workflow/templates")
@RequiredArgsConstructor
public class WorkflowTemplateController {

    private final WorkflowTemplateQueryService workflowTemplateQueryService;

    @GetMapping
    public Result<List<WorkflowTemplateVO>> list() {
        return Results.success(workflowTemplateQueryService.list());
    }

    @GetMapping("/{id}")
    public Result<WorkflowTemplateVO> detail(@PathVariable Long id) {
        return Results.success(workflowTemplateQueryService.detail(id));
    }
}
