package com.zjl.collaboration.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public Result<List<Map<String,Object>>> list(@RequestParam(required=false) String status) {
        String sql = "SELECT t.*, u.real_name as assignee_name FROM sys_task t LEFT JOIN sys_user u ON t.assignee_id=u.id";
        if (status != null) sql += " WHERE t.status=? ORDER BY t.created_at DESC";
        else sql += " ORDER BY t.created_at DESC";
        return Results.success(status != null ? jdbc.queryForList(sql, status) : jdbc.queryForList(sql));
    }

    @PostMapping
    public Result<Long> create(@RequestBody TaskReq req, @RequestHeader("X-User-Id") Long userId) {
        jdbc.update("INSERT INTO sys_task (title,description,creator_id,assignee_id,priority,status,due_date,created_at) VALUES (?,?,?,?,?,?,?,?)",
            req.getTitle(), req.getDescription(), userId, req.getAssigneeId(), req.getPriority(), "todo", req.getDueDate(), LocalDateTime.now());
        return Results.success(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskReq req) {
        jdbc.update("UPDATE sys_task SET title=?,description=?,assignee_id=?,priority=?,due_date=?,updated_at=? WHERE id=?",
            req.getTitle(), req.getDescription(), req.getAssigneeId(), req.getPriority(), req.getDueDate(), LocalDateTime.now(), id);
        return Results.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String,String> body) {
        jdbc.update("UPDATE sys_task SET status=?, updated_at=? WHERE id=?", body.get("status"), LocalDateTime.now(), id);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM sys_task WHERE id=?", id);
        return Results.success();
    }

    @GetMapping("/{taskId}/comments")
    public Result<List<Map<String,Object>>> comments(@PathVariable Long taskId) {
        return Results.success(jdbc.queryForList("SELECT * FROM sys_task_comment WHERE task_id=? ORDER BY created_at ASC", taskId));
    }

    @PostMapping("/{taskId}/comments")
    public Result<Void> addComment(@PathVariable Long taskId, @RequestBody Map<String,String> body, @RequestHeader("X-User-Id") Long userId) {
        var user = jdbc.queryForMap("SELECT real_name FROM sys_user WHERE id=?", userId);
        jdbc.update("INSERT INTO sys_task_comment (task_id,user_id,user_name,content,created_at) VALUES (?,?,?,?,?)",
            taskId, userId, user.get("real_name"), body.get("content"), LocalDateTime.now());
        return Results.success();
    }

    @Data public static class TaskReq {
        private String title; private String description; private Long assigneeId;
        private String priority; private String dueDate;
    }
}
