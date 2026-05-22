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

import java.time.LocalDate;
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

    @GetMapping("/my")
    public Result<List<SysMeeting>> listMy(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "") String userName) {
        List<SysMeeting> all = meetingMapper.selectList(
                Wrappers.lambdaQuery(SysMeeting.class)
                        .orderByDesc(SysMeeting::getDate)
                        .orderByAsc(SysMeeting::getStartTime)
        );
        String name = userName.isBlank() ? String.valueOf(userId) : userName;
        List<SysMeeting> mine = all.stream()
                .filter(m -> m.getCreatorId().equals(userId)
                        || (m.getAttendees() != null && m.getAttendees().contains(name)))
                .collect(java.util.stream.Collectors.toList());
        return Results.success(mine);
    }

    @PostMapping
    public Result<Long> create(@RequestBody MeetingReq req, @RequestHeader("X-User-Id") Long userId) {
        SysMeeting m = new SysMeeting();
        m.setTitle(req.getTitle()); 
        m.setRoom(req.getRoom()); 
        m.setCreatorId(userId);
        m.setDate(LocalDate.parse(req.getDate())); 
        m.setStartTime(req.getStartTime()); 
        m.setEndTime(req.getEndTime());
        m.setAttendees(req.getAttendees());
        m.setDescription(req.getDescription());
        m.setStatus("confirmed");

        if ("线上-Zoom".equals(req.getRoom()) && zoomClient.isConfigured()) {
            try {
                String startTime = req.getDate() + "T" + (req.getStartTime() != null ? req.getStartTime() : "10:00") + ":00";
                log.info("Creating Zoom meeting: title={}, startTime={}", req.getTitle(), startTime);
                Map<String, Object> zoomResp = zoomClient.createMeeting(req.getTitle(), startTime, 60);
                if (zoomResp != null) {
                    m.setMeetingId(String.valueOf(zoomResp.get("id")));
                    m.setJoinUrl(String.valueOf(zoomResp.get("join_url")));
                    log.info("Zoom meeting created: id={}, joinUrl={}", m.getMeetingId(), m.getJoinUrl());
                } else {
                    log.warn("Zoom API returned null response");
                }
            } catch (Exception e) { 
                log.warn("Zoom API failed, creating local meeting only", e); 
            }
        } else {
            if ("线上-Zoom".equals(req.getRoom())) {
                log.warn("Zoom is not configured, cannot create Zoom meeting");
            }
        }

        meetingMapper.insert(m);
        return Results.success(m.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MeetingReq req) {
        SysMeeting m = meetingMapper.selectById(id);
        if (m == null) return Results.success();
        m.setTitle(req.getTitle()); 
        m.setRoom(req.getRoom()); 
        m.setDate(LocalDate.parse(req.getDate()));
        m.setStartTime(req.getStartTime()); 
        m.setEndTime(req.getEndTime()); 
        m.setAttendees(req.getAttendees());
        m.setDescription(req.getDescription());
        meetingMapper.updateById(m);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { meetingMapper.deleteById(id); return Results.success(); }

    @Data public static class MeetingReq { private String title; private String room; private String date; private String startTime; private String endTime; private String attendees; private String description; }
}
