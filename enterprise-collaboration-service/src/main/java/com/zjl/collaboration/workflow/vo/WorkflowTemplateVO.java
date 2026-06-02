package com.zjl.collaboration.workflow.vo;

import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WorkflowTemplateVO {
    private Long id;
    private String code;
    private String name;
    private String businessType;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<WfNode> nodes;
    private Map<Long, List<WfNodeApprover>> approversByNodeId;
}
