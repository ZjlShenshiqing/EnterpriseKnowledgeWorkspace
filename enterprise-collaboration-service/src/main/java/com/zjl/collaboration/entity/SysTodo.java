package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;
@Data
@TableName("sys_todo")
public class SysTodo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long userId;
    private String priority;
    private Date dueDate;
    private Integer done;
    private LocalDateTime createdAt;
}
