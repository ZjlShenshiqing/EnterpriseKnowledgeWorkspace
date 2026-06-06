package com.zjl.collaboration.web;

import com.zjl.collaboration.dto.TodoReq;
import com.zjl.collaboration.entity.SysTodo;
import com.zjl.collaboration.service.TodoService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 待办接口。
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public Result<List<SysTodo>> list(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(todoService.list(userId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody TodoReq req, @RequestHeader("X-User-Id") Long userId) {
        return Results.success(todoService.create(req.getTitle(), req.getPriority(), req.getDueDate(), userId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TodoReq req) {
        todoService.update(id, req.getTitle(), req.getPriority(), req.getDueDate());
        return Results.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        todoService.toggle(id);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return Results.success();
    }

}
