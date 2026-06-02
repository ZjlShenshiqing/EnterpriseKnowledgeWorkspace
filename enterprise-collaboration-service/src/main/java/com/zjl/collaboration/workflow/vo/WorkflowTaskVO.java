package com.zjl.collaboration.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTaskVO {
    private Long id;
    private Long instanceId;
    private Long nodeId;
    private String assigneeType;
    private Long assigneeId;
    private String status;
    private Long claimedBy;
    private LocalDateTime handledAt;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
