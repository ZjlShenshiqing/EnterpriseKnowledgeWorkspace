package com.zjl.collaboration.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_node_approver")
public class WfNodeApprover {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private String approverType;
    private Long approverId;
    private LocalDateTime createdAt;
}
