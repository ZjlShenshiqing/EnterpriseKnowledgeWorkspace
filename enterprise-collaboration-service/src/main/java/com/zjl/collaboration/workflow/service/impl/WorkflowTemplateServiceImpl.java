package com.zjl.collaboration.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfTemplate;
import com.zjl.collaboration.workflow.enums.WfNodeType;
import com.zjl.collaboration.workflow.mapper.WfNodeApproverMapper;
import com.zjl.collaboration.workflow.mapper.WfNodeMapper;
import com.zjl.collaboration.workflow.mapper.WfTemplateMapper;
import com.zjl.collaboration.workflow.service.WorkflowTemplateService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateServiceImpl implements WorkflowTemplateService {
    private final WfTemplateMapper templateMapper;
    private final WfNodeMapper nodeMapper;
    private final WfNodeApproverMapper nodeApproverMapper;

    @Override
    public WfTemplate requireEnabledTemplate(String businessType, String code) {
        WfTemplate template = templateMapper.selectOne(Wrappers.lambdaQuery(WfTemplate.class)
                .eq(WfTemplate::getBusinessType, businessType)
                .eq(WfTemplate::getCode, code)
                .eq(WfTemplate::getEnabled, 1)
                .eq(WfTemplate::getDeleted, 0));
        if (template == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程模板不存在或未启用");
        }
        return template;
    }

    @Override
    public List<WfNode> listNodes(Long templateId) {
        return nodeMapper.selectList(Wrappers.lambdaQuery(WfNode.class)
                .eq(WfNode::getTemplateId, templateId)
                .eq(WfNode::getDeleted, 0)
                .orderByAsc(WfNode::getSortOrder));
    }

    @Override
    public WfNode requireFirstApprovalNode(Long templateId) {
        return listNodes(templateId).stream()
                .filter(node -> Objects.equals(WfNodeType.APPROVAL, node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "流程模板缺少审批节点"));
    }

    @Override
    public WfNode findNextApprovalNode(Long templateId, Integer currentSortOrder) {
        return listNodes(templateId).stream()
                .filter(node -> node.getSortOrder() != null)
                .filter(node -> node.getSortOrder() > currentSortOrder)
                .filter(node -> Objects.equals(WfNodeType.APPROVAL, node.getNodeType()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<WfNodeApprover> requireApprovers(Long nodeId) {
        List<WfNodeApprover> approvers = nodeApproverMapper.selectList(
                Wrappers.lambdaQuery(WfNodeApprover.class).eq(WfNodeApprover::getNodeId, nodeId));
        if (approvers.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批节点缺少审批人");
        }
        return approvers;
    }
}
