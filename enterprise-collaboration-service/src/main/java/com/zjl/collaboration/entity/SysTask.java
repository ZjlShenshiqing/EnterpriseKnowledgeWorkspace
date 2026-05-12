package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;
@Data
@TableName("sys_task")
public class SysTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private Long creatorId;
    private Long assigneeId;
    private String priority;
    private String status;
    private Date dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
