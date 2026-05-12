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
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public Result<List<Map<String,Object>>> list() {
        return Results.success(jdbc.queryForList("SELECT * FROM sys_meeting ORDER BY date DESC, start_time ASC"));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MeetingReq req, @RequestHeader("X-User-Id") Long userId) {
        jdbc.update("INSERT INTO sys_meeting (title,room,creator_id,date,start_time,end_time,attendees,status) VALUES (?,?,?,?,?,?,?,?)",
            req.getTitle(), req.getRoom(), userId, req.getDate(), req.getStartTime(), req.getEndTime(), req.getAttendees(), "confirmed");
        return Results.success(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MeetingReq req) {
        jdbc.update("UPDATE sys_meeting SET title=?,room=?,date=?,start_time=?,end_time=?,attendees=? WHERE id=?",
            req.getTitle(), req.getRoom(), req.getDate(), req.getStartTime(), req.getEndTime(), req.getAttendees(), id);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM sys_meeting WHERE id=?", id);
        return Results.success();
    }

    @Data public static class MeetingReq {
        private String title; private String room; private String date;
        private String startTime; private String endTime; private String attendees;
    }
}
