package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会议保存请求。
 */
@Data
public class MeetingReq {
    @NotBlank(message = "会议标题不能为空")
    private String title;
    private String room;
    private String date;
    private String startTime;
    private String endTime;
    private String attendees;
    private String description;
}
