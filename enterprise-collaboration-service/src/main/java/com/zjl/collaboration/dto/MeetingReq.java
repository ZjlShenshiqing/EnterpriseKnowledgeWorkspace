package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 会议保存请求。
 */
@Data
public class MeetingReq {
    private String title;
    private String room;
    private String date;
    private String startTime;
    private String endTime;
    private String attendees;
    private String description;
}
