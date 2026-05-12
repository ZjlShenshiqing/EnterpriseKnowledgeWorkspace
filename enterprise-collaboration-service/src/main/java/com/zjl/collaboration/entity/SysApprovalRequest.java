package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("sys_approval_request")
public class SysApprovalRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private Long userId;
    private String userName;
    private String title;
    private String formData;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
