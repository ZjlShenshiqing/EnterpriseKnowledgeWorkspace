package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysMeeting;
import com.zjl.collaboration.integration.WorkbenchCacheNotifier;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.collaboration.integration.ZoomMeetingClient;
import com.zjl.collaboration.mapper.SysMeetingMapper;
import com.zjl.collaboration.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 会议业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {

    private static final String ZOOM_ROOM = "线上-Zoom";

    private final SysMeetingMapper meetingMapper;
    private final ZoomMeetingClient zoomClient;
    private final WorkbenchCacheNotifier workbenchCacheNotifier;

    @Override
    public List<SysMeeting> list() {
        return meetingMapper.selectList(Wrappers.lambdaQuery(SysMeeting.class)
                .orderByDesc(SysMeeting::getDate)
                .orderByAsc(SysMeeting::getStartTime));
    }

    @Override
    public List<SysMeeting> listMine(Long userId, String userName) {
        String name = userName == null || userName.isBlank() ? String.valueOf(userId) : userName;
        return list().stream()
                .filter(m -> userId.equals(m.getCreatorId())
                        || (m.getAttendees() != null && m.getAttendees().contains(name)))
                .toList();
    }

    @Override
    public Long create(String title, String room, String date, String startTime, String endTime,
                       String attendees, String description, Long userId) {
        SysMeeting meeting = new SysMeeting();
        meeting.setTitle(title);
        meeting.setRoom(room);
        meeting.setCreatorId(userId);
        meeting.setDate(LocalDate.parse(date));
        meeting.setStartTime(startTime);
        meeting.setEndTime(endTime);
        meeting.setAttendees(attendees);
        meeting.setDescription(description);
        meeting.setStatus("confirmed");
        fillZoomInfoIfNeeded(meeting, date, startTime);
        meetingMapper.insert(meeting);
        workbenchCacheNotifier.evictOverview(userId);
        return meeting.getId();
    }

    @Override
    public void update(Long id, String title, String room, String date, String startTime, String endTime,
                       String attendees, String description) {
        SysMeeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会议不存在");
        }
        meeting.setTitle(title);
        meeting.setRoom(room);
        meeting.setDate(LocalDate.parse(date));
        meeting.setStartTime(startTime);
        meeting.setEndTime(endTime);
        meeting.setAttendees(attendees);
        meeting.setDescription(description);
        meetingMapper.updateById(meeting);
        workbenchCacheNotifier.evictOverview(meeting.getCreatorId());
    }

    @Override
    public void delete(Long id) {
        SysMeeting existing = meetingMapper.selectById(id);
        meetingMapper.deleteById(id);
        if (existing != null) {
            workbenchCacheNotifier.evictOverview(existing.getCreatorId());
        }
    }

    @Override
    public ConflictCheckResult checkConflict(String date, String startTime, String endTime, String room, Long excludeId) {
        if (date == null || date.isBlank()
                || startTime == null || startTime.isBlank()
                || endTime == null || endTime.isBlank()
                || room == null || room.isBlank()) {
            return new ConflictCheckResult(false, List.of(), "参数完整，未检测到冲突");
        }
        if (ZOOM_ROOM.equals(room)) {
            return new ConflictCheckResult(false, List.of(), "线上会议不检测会议室占用");
        }
        LocalDate meetingDate = LocalDate.parse(date);
        List<SysMeeting> sameDayRoom = meetingMapper.selectList(
                Wrappers.lambdaQuery(SysMeeting.class)
                        .eq(SysMeeting::getDate, meetingDate)
                        .eq(SysMeeting::getRoom, room)
                        .ne(excludeId != null, SysMeeting::getId, excludeId));
        List<SysMeeting> conflicts = sameDayRoom.stream()
                .filter(m -> m.getStartTime() != null && m.getEndTime() != null)
                .filter(m -> overlaps(startTime, endTime, m.getStartTime(), m.getEndTime()))
                .toList();
        if (conflicts.isEmpty()) {
            return new ConflictCheckResult(false, List.of(), "该时段会议室可用");
        }
        return new ConflictCheckResult(true, conflicts,
                "该时段 " + room + " 已被占用，共 " + conflicts.size() + " 个冲突会议");
    }

    private void fillZoomInfoIfNeeded(SysMeeting meeting, String date, String startTime) {
        if (!ZOOM_ROOM.equals(meeting.getRoom())) {
            return;
        }
        if (!zoomClient.isConfigured()) {
            log.warn("Zoom is not configured, cannot create Zoom meeting");
            return;
        }
        try {
            String zoomStartTime = date + "T" + (startTime != null ? startTime : "10:00") + ":00";
            log.info("Creating Zoom meeting: title={}, startTime={}", meeting.getTitle(), zoomStartTime);
            Map<String, Object> zoomResp = zoomClient.createMeeting(meeting.getTitle(), zoomStartTime, 60);
            if (zoomResp != null) {
                meeting.setMeetingId(String.valueOf(zoomResp.get("id")));
                meeting.setJoinUrl(String.valueOf(zoomResp.get("join_url")));
                log.info("Zoom meeting created: id={}, joinUrl={}", meeting.getMeetingId(), meeting.getJoinUrl());
            }
        } catch (Exception e) {
            log.warn("Zoom API failed, creating local meeting only", e);
        }
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
}
