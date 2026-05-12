package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("sys_approval_record")
public class SysApprovalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long approverId;
    private String approverName;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
