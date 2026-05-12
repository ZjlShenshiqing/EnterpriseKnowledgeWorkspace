package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.mapper.SysUserMapper;
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
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final JdbcTemplate jdbc;
    private final SysUserMapper userMapper;

    @GetMapping
    public Result<List<Map<String,Object>>> list() {
        List<Map<String,Object>> list = jdbc.queryForList(
            "SELECT * FROM sys_announcement ORDER BY is_pinned DESC, created_at DESC");
        return Results.success(list);
    }

    @PostMapping
    public Result<Void> publish(@RequestBody AnnounceReq req,
                                 @RequestHeader("X-User-Id") Long userId) {
        var user = userMapper.selectById(userId);
        jdbc.update("INSERT INTO sys_announcement (title,content,publisher_id,publisher_name,created_at) VALUES (?,?,?,?,?)",
            req.getTitle(), req.getContent(), userId,
            user != null ? user.getRealName() : "管理员", LocalDateTime.now());
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM sys_announcement WHERE id=?", id);
        return Results.success();
    }

    @Data public static class AnnounceReq { private String title; private String content; }
}
