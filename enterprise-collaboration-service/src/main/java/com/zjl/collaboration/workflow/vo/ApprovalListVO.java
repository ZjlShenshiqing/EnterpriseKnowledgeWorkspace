package com.zjl.collaboration.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalListVO {
    private Long id;
    private String type;
    private Long userId;
    private String userName;
    private String title;
    private String status;
    private Long workflowInstanceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
