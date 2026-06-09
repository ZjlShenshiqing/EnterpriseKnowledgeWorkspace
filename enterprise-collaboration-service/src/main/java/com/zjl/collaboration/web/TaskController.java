package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.dto.TaskReq;
import com.zjl.collaboration.entity.SysTaskComment;
import com.zjl.collaboration.service.TaskService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 任务接口。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        return Results.success(taskService.list(status));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody TaskReq req, @RequestHeader("X-User-Id") Long userId) {
        return Results.success(taskService.create(req.getTitle(), req.getDescription(),
                req.getAssigneeId(), req.getPriority(), userId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TaskReq req) {
        taskService.update(id, req.getTitle(), req.getDescription(), req.getAssigneeId(), req.getPriority());
        return Results.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        taskService.updateStatus(id, body.get("status"));
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Results.success();
    }

    @GetMapping("/{taskId}/comments")
    public Result<List<SysTaskComment>> comments(@PathVariable Long taskId) {
        return Results.success(taskService.comments(taskId));
    }

    @PostMapping("/{taskId}/comments")
    public Result<Void> addComment(@PathVariable Long taskId,
                                   @RequestBody Map<String, String> body,
                                   @RequestHeader("X-User-Id") Long userId) {
        taskService.addComment(taskId, body.get("content"), userId);
        return Results.success();
    }

}
