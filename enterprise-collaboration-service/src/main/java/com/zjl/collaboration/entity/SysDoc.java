package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("sys_doc")
public class SysDoc {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private Long updatedBy;
    private String updatedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
