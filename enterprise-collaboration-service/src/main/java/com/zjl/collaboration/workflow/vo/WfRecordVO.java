package com.zjl.collaboration.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfRecordVO {
    private Long id;
    private Long instanceId;
    private Long nodeId;
    private Long taskId;
    private Long operatorId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private LocalDateTime createdAt;
}
