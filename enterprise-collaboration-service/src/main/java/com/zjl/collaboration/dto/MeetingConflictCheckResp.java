package com.zjl.collaboration.dto;

import com.zjl.collaboration.entity.SysMeeting;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 会议冲突检查响应。
 */
@Data
@AllArgsConstructor
public class MeetingConflictCheckResp {
    private boolean conflict;
    private List<SysMeeting> conflicts;
    private String message;
}
