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
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final JdbcTemplate jdbc;

    @GetMapping("/conversations")
    public Result<List<Map<String,Object>>> conversations(@RequestHeader("X-User-Id") Long userId) {
        List<Map<String,Object>> list = jdbc.queryForList(
            "SELECT c.*, (SELECT content FROM im_message WHERE conversation_id=c.id ORDER BY created_at DESC LIMIT 1) as last_msg FROM im_conversation c WHERE c.id IN (SELECT conversation_id FROM im_conversation_member WHERE user_id=?) ORDER BY c.updated_at DESC", userId);
        return Results.success(list);
    }

    @GetMapping("/messages/{convId}")
    public Result<List<Map<String,Object>>> messages(@PathVariable Long convId) {
        return Results.success(jdbc.queryForList(
            "SELECT * FROM im_message WHERE conversation_id=? ORDER BY created_at ASC LIMIT 100", convId));
    }

    @PostMapping("/conversations")
    public Result<Long> createConv(@RequestBody CreateConvReq req, @RequestHeader("X-User-Id") Long userId) {
        jdbc.update("INSERT INTO im_conversation (name, type, created_by, created_at) VALUES (?,?,?,?)",
            req.getName(), req.getType(), userId, LocalDateTime.now());
        Long convId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO im_conversation_member (conversation_id, user_id) VALUES (?,?)", convId, userId);
        for (Long uid : req.getMemberIds()) {
            jdbc.update("INSERT INTO im_conversation_member (conversation_id, user_id) VALUES (?,?)", convId, uid);
        }
        return Results.success(convId);
    }

    @GetMapping("/members/{convId}")
    public Result<List<Map<String,Object>>> members(@PathVariable Long convId) {
        return Results.success(jdbc.queryForList(
            "SELECT u.id, u.username, u.real_name FROM sys_user u JOIN im_conversation_member m ON u.id=m.user_id WHERE m.conversation_id=?", convId));
    }

    @Data public static class CreateConvReq { private String name; private String type; private List<Long> memberIds; }
}
