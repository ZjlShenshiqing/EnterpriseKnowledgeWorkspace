package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysMeeting;
import com.zjl.collaboration.integration.ZoomMeetingClient;
import com.zjl.collaboration.mapper.SysMeetingMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final SysMeetingMapper meetingMapper;
    private final ZoomMeetingClient zoomClient;

    @GetMapping
    public Result<List<SysMeeting>> list() {
        return Results.success(meetingMapper.selectList(Wrappers.lambdaQuery(SysMeeting.class).orderByDesc(SysMeeting::getDate).orderByAsc(SysMeeting::getStartTime)));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MeetingReq req, @RequestHeader("X-User-Id") Long userId) {
        SysMeeting m = new SysMeeting();
        m.setTitle(req.getTitle()); m.setRoom(req.getRoom()); m.setCreatorId(userId);
        m.setDate(req.getDate()); m.setStartTime(req.getStartTime()); m.setEndTime(req.getEndTime());
        m.setAttendees(req.getAttendees()); m.setStatus("confirmed");

        if ("线上-Zoom".equals(req.getRoom()) && zoomClient.isConfigured()) {
            try {
                String startTime = req.getDate() + "T" + (req.getStartTime() != null ? req.getStartTime() : "10:00") + ":00";
                Map<String, Object> zoomResp = zoomClient.createMeeting(req.getTitle(), startTime, 60);
                if (zoomResp != null) {
                    m.setMeetingId(String.valueOf(zoomResp.get("id")));
                    m.setJoinUrl(String.valueOf(zoomResp.get("join_url")));
                }
            } catch (Exception e) { log.warn("Zoom API failed, creating local meeting only", e); }
        }

        meetingMapper.insert(m);
        return Results.success(m.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MeetingReq req) {
        SysMeeting m = meetingMapper.selectById(id);
        if (m == null) return Results.success();
        m.setTitle(req.getTitle()); m.setRoom(req.getRoom()); m.setDate(req.getDate());
        m.setStartTime(req.getStartTime()); m.setEndTime(req.getEndTime()); m.setAttendees(req.getAttendees());
        meetingMapper.updateById(m);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { meetingMapper.deleteById(id); return Results.success(); }

    @Data public static class MeetingReq { private String title; private String room; private String date; private String startTime; private String endTime; private String attendees; }
}
