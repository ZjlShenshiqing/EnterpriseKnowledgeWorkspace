package com.zjl.collaboration.workflow.vo;

import com.zjl.collaboration.workflow.entity.WfInstance;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ApprovalDetailVO {
    private Long id;
    private String type;
    private Long userId;
    private String userName;
    private String title;
    private String formData;
    private String status;
    private Long workflowInstanceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private WfInstance instance;
    private List<WfRecordVO> records;
}
