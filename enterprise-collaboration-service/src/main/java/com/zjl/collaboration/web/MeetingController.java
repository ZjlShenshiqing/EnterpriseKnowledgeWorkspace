package com.zjl.collaboration.web;

import com.zjl.collaboration.dto.MeetingConflictCheckReq;
import com.zjl.collaboration.dto.MeetingConflictCheckResp;
import com.zjl.collaboration.dto.MeetingReq;
import com.zjl.collaboration.entity.SysMeeting;
import com.zjl.collaboration.service.MeetingService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会议接口。
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping
    public Result<List<SysMeeting>> list() {
        return Results.success(meetingService.list());
    }

    @GetMapping("/my")
    public Result<List<SysMeeting>> listMy(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "") String userName) {
        return Results.success(meetingService.listMine(userId, userName));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MeetingReq req, @RequestHeader("X-User-Id") Long userId) {
        return Results.success(meetingService.create(req.getTitle(), req.getRoom(), req.getDate(),
                req.getStartTime(), req.getEndTime(), req.getAttendees(), req.getDescription(), userId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MeetingReq req) {
        meetingService.update(id, req.getTitle(), req.getRoom(), req.getDate(), req.getStartTime(),
                req.getEndTime(), req.getAttendees(), req.getDescription());
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        meetingService.delete(id);
        return Results.success();
    }

    @PostMapping("/check-conflict")
    public Result<MeetingConflictCheckResp> checkConflict(@RequestBody MeetingConflictCheckReq req) {
        MeetingService.ConflictCheckResult result = meetingService.checkConflict(
                req.getDate(), req.getStartTime(), req.getEndTime(), req.getRoom(), req.getExcludeId());
        return Results.success(new MeetingConflictCheckResp(result.conflict(), result.conflicts(), result.message()));
    }
}
