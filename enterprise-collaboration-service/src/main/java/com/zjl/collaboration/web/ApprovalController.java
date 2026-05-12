package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping
    public Result<List<Map<String,Object>>> list(@RequestHeader("X-User-Id") Long userId,
                                                   @RequestHeader("X-Is-Admin") String isAdmin) {
        String sql = "true".equals(isAdmin) ? "SELECT * FROM sys_approval_request ORDER BY created_at DESC"
            : "SELECT * FROM sys_approval_request WHERE user_id=? ORDER BY created_at DESC";
        return Results.success("true".equals(isAdmin) ? jdbc.queryForList(sql) : jdbc.queryForList(sql, userId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody Map<String,Object> body,
                                @RequestHeader("X-User-Id") Long userId) {
        var user = jdbc.queryForMap("SELECT real_name FROM sys_user WHERE id=?", userId);
        String title = body.get("title").toString();
        String type = body.get("type").toString();
        jdbc.update("INSERT INTO sys_approval_request (type,user_id,user_name,title,form_data,status,created_at) VALUES (?,?,?,?,?,?,?)",
            type, userId, user.get("real_name"), title, mapper.writeValueAsString(body.get("formData")), "pending", LocalDateTime.now());
        return Results.success(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @GetMapping("/{id}")
    public Result<Map<String,Object>> detail(@PathVariable Long id) {
        var req = jdbc.queryForMap("SELECT * FROM sys_approval_request WHERE id=?", id);
        var records = jdbc.queryForList("SELECT * FROM sys_approval_record WHERE request_id=? ORDER BY created_at ASC", id);
        req.put("records", records);
        return Results.success(req);
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody Map<String,String> body,
                                 @RequestHeader("X-User-Id") Long userId) {
        var user = jdbc.queryForMap("SELECT real_name FROM sys_user WHERE id=?", userId);
        jdbc.update("INSERT INTO sys_approval_record (request_id,approver_id,approver_name,action,comment,created_at) VALUES (?,?,?,?,?,?)",
            id, userId, user.get("real_name"), body.get("action"), body.get("comment"), LocalDateTime.now());

        String newStatus = "rejected".equals(body.get("action")) ? "rejected" : nextStatus(jdbc.queryForMap("SELECT type,status FROM sys_approval_request WHERE id=?", id));
        jdbc.update("UPDATE sys_approval_request SET status=?, updated_at=? WHERE id=?", newStatus, LocalDateTime.now(), id);
        return Results.success();
    }

    private String nextStatus(Map<String,Object> req) {
        String type = req.get("type").toString();
        String status = req.get("status").toString();
        if ("leave".equals(type)) {
            return switch (status) { case "pending"->"manager_approved"; case "manager_approved"->"approved"; default->status; };
        }
        return switch (status) { case "pending"->"manager_approved"; case "manager_approved"->"finance_approved"; case "finance_approved"->"approved"; default->status; };
    }
}
