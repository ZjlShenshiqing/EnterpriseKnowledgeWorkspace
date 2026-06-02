package com.zjl.collaboration.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_instance")
public class WfInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String businessType;
    private Long businessId;
    private Long starterId;
    private String status;
    private Long currentNodeId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
