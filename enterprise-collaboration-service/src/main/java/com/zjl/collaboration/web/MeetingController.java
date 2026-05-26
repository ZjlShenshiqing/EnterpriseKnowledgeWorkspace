package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysMeeting;
import com.zjl.collaboration.integration.WorkbenchCacheNotifier;
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
    private final WorkbenchCacheNotifier workbenchCacheNotifier;

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
        workbenchCacheNotifier.evictOverview(userId);
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
        workbenchCacheNotifier.evictOverview(m.getCreatorId());
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysMeeting existing = meetingMapper.selectById(id);
        meetingMapper.deleteById(id);
        if (existing != null) {
            workbenchCacheNotifier.evictOverview(existing.getCreatorId());
        }
        return Results.success();
    }

    /**
     * 检测会议室时间冲突（同一线下会议室、同一日期、时间段重叠）。
     */
    @PostMapping("/check-conflict")
    public Result<ConflictCheckResp> checkConflict(@RequestBody ConflictCheckReq req) {
        if (req.getDate() == null || req.getDate().isBlank()
                || req.getStartTime() == null || req.getStartTime().isBlank()
                || req.getEndTime() == null || req.getEndTime().isBlank()
                || req.getRoom() == null || req.getRoom().isBlank()) {
            return Results.success(new ConflictCheckResp(false, List.of(), "参数完整，未检测到冲突"));
        }

        if ("线上-Zoom".equals(req.getRoom())) {
            return Results.success(new ConflictCheckResp(false, List.of(), "线上会议不检测会议室占用"));
        }

        LocalDate date = LocalDate.parse(req.getDate());
        List<SysMeeting> sameDayRoom = meetingMapper.selectList(
                Wrappers.lambdaQuery(SysMeeting.class)
                        .eq(SysMeeting::getDate, date)
                        .eq(SysMeeting::getRoom, req.getRoom())
                        .ne(req.getExcludeId() != null, SysMeeting::getId, req.getExcludeId())
        );

        List<SysMeeting> conflicts = sameDayRoom.stream()
                .filter(m -> m.getStartTime() != null && m.getEndTime() != null)
                .filter(m -> overlaps(req.getStartTime(), req.getEndTime(), m.getStartTime(), m.getEndTime()))
                .toList();

        if (conflicts.isEmpty()) {
            return Results.success(new ConflictCheckResp(false, List.of(), "该时段会议室可用"));
        }
        return Results.success(new ConflictCheckResp(true, conflicts,
                "该时段 " + req.getRoom() + " 已被占用，共 " + conflicts.size() + " 个冲突会议"));
    }

    private static boolean overlaps(String startA, String endA, String startB, String endB) {
        return toMinutes(startA) < toMinutes(endB) && toMinutes(endA) > toMinutes(startB);
    }

    private static int toMinutes(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return hour * 60 + minute;
    }

    @Data public static class MeetingReq { private String title; private String room; private String date; private String startTime; private String endTime; private String attendees; private String description; }

    @Data public static class ConflictCheckReq {
        private String date;
        private String startTime;
        private String endTime;
        private String room;
        private Long excludeId;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class ConflictCheckResp {
        private boolean conflict;
        private List<SysMeeting> conflicts;
        private String message;
    }
}
