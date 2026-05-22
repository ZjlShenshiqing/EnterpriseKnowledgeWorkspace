package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@TableName("sys_meeting")
public class SysMeeting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String room;
    private Long creatorId;
    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @JsonProperty("start_time")
    private String startTime;
    @JsonProperty("end_time")
    private String endTime;
    private String attendees;
    /** 会议备注/议程 */
    private String description;
    private String status;
    @TableField("join_url")
    @JsonProperty("join_url")
    private String joinUrl;
    @TableField("meeting_id")
    @JsonProperty("meeting_id")
    private String meetingId;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
