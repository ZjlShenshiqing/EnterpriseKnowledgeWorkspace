package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("sys_meeting")
public class SysMeeting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String room;
    private Long creatorId;
    private String date;
    private String startTime;
    private String endTime;
    private String attendees;
    private String status;
    private String joinUrl;
    private String meetingId;
    private LocalDateTime createdAt;
}
