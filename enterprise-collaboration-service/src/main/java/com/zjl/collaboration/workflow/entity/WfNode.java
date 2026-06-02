package com.zjl.collaboration.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_node")
public class WfNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String nodeKey;
    private String nodeName;
    private String nodeType;
    private Integer sortOrder;
    private String approvalMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
