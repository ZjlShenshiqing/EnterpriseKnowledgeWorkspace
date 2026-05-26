package com.zjl.knowledge.web;

import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.dto.pipeline.PipelineTaskVO;
import com.zjl.knowledge.dto.pipeline.PipelineVO;
import com.zjl.knowledge.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流水线管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * 流水线列表。
     */
    @GetMapping
    public Result<List<PipelineVO>> list(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(required = false) String status
    ) {
        List<PipelineVO> list = pipelineService.listPipelines(knowledgeBaseId, status);
        return Results.success(list);
    }

    /**
     * 流水线详情。
     */
    @GetMapping("/{id}")
    public Result<PipelineVO> detail(@PathVariable Long id) {
        PipelineVO vo = pipelineService.getPipeline(id);
        return Results.success(vo);
    }

    /**
     * 流水线任务列表（分页）。
     */
    @GetMapping("/tasks")
    public Result<PageResult<PipelineTaskVO>> tasks(
            @RequestParam(required = false) Long pipelineId,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<PipelineTaskVO> records = pipelineService.listTasks(pipelineId, documentId, status, current, size);
        long total = pipelineService.countTasks(pipelineId, documentId, status);
        return Results.success(PageResult.of(current, size, total, records));
    }
}
