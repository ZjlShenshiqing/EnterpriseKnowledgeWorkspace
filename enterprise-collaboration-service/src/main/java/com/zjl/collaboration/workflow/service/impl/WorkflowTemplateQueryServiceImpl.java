package com.zjl.collaboration.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfTemplate;
import com.zjl.collaboration.workflow.mapper.WfNodeApproverMapper;
import com.zjl.collaboration.workflow.mapper.WfNodeMapper;
import com.zjl.collaboration.workflow.mapper.WfTemplateMapper;
import com.zjl.collaboration.workflow.service.WorkflowTemplateQueryService;
import com.zjl.collaboration.workflow.vo.WorkflowTemplateVO;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流模板查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class WorkflowTemplateQueryServiceImpl implements WorkflowTemplateQueryService {

    private final WfTemplateMapper templateMapper;
    private final WfNodeMapper nodeMapper;
    private final WfNodeApproverMapper approverMapper;

    @Override
    public List<WorkflowTemplateVO> list() {
        return templateMapper.selectList(Wrappers.lambdaQuery(WfTemplate.class)
                        .eq(WfTemplate::getDeleted, 0)
                        .orderByAsc(WfTemplate::getId))
                .stream()
                .map(template -> toVO(template, false))
                .toList();
    }

    @Override
    public WorkflowTemplateVO detail(Long id) {
        WfTemplate template = templateMapper.selectById(id);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流模板不存在");
        }
        return toVO(template, true);
    }

    private WorkflowTemplateVO toVO(WfTemplate template, boolean includeApprovers) {
        List<WfNode> nodes = nodeMapper.selectList(Wrappers.lambdaQuery(WfNode.class)
                .eq(WfNode::getTemplateId, template.getId())
                .eq(WfNode::getDeleted, 0)
                .orderByAsc(WfNode::getSortOrder));
        Map<Long, List<WfNodeApprover>> approversByNodeId = includeApprovers && !nodes.isEmpty()
                ? approverMapper.selectList(Wrappers.lambdaQuery(WfNodeApprover.class)
                        .in(WfNodeApprover::getNodeId, nodes.stream().map(WfNode::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(WfNodeApprover::getNodeId))
                : Map.of();
        WorkflowTemplateVO vo = new WorkflowTemplateVO();
        vo.setId(template.getId());
        vo.setCode(template.getCode());
        vo.setName(template.getName());
        vo.setBusinessType(template.getBusinessType());
        vo.setEnabled(template.getEnabled());
        vo.setCreatedAt(template.getCreatedAt());
        vo.setUpdatedAt(template.getUpdatedAt());
        vo.setNodes(nodes);
        vo.setApproversByNodeId(approversByNodeId);
        return vo;
    }
}
