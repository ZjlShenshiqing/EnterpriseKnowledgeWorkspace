package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfTemplate;

import java.util.List;

public interface WorkflowTemplateService {
    WfTemplate requireEnabledTemplate(String businessType, String code);

    List<WfNode> listNodes(Long templateId);

    WfNode requireFirstApprovalNode(Long templateId);

    WfNode findNextApprovalNode(Long templateId, Integer currentSortOrder);

    List<WfNodeApprover> requireApprovers(Long nodeId);
}
