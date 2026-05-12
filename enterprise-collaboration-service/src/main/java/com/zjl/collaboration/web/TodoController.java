package com.zjl.collaboration.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public Result<List<Map<String,Object>>> list(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(jdbc.queryForList("SELECT * FROM sys_todo WHERE user_id=? ORDER BY done ASC, due_date ASC", userId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody TodoReq req, @RequestHeader("X-User-Id") Long userId) {
        jdbc.update("INSERT INTO sys_todo (title,user_id,priority,due_date,done) VALUES (?,?,?,?,0)",
            req.getTitle(), userId, req.getPriority(), req.getDueDate());
        return Results.success(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TodoReq req) {
        jdbc.update("UPDATE sys_todo SET title=?,priority=?,due_date=? WHERE id=?", req.getTitle(), req.getPriority(), req.getDueDate(), id);
        return Results.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        jdbc.update("UPDATE sys_todo SET done = 1 - done WHERE id=?", id);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM sys_todo WHERE id=?", id);
        return Results.success();
    }

    @Data public static class TodoReq { private String title; private String priority; private String dueDate; }
}
