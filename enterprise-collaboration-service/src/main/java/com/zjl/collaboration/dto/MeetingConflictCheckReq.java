package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 会议冲突检查请求。
 */
@Data
public class MeetingConflictCheckReq {
    private String date;
    private String startTime;
    private String endTime;
    private String room;
    private Long excludeId;
}
