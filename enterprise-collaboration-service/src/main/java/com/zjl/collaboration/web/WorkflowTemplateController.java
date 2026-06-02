package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfTemplate;
import com.zjl.collaboration.workflow.mapper.WfNodeApproverMapper;
import com.zjl.collaboration.workflow.mapper.WfNodeMapper;
import com.zjl.collaboration.workflow.mapper.WfTemplateMapper;
import com.zjl.collaboration.workflow.vo.WorkflowTemplateVO;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflow/templates")
@RequiredArgsConstructor
public class WorkflowTemplateController {
    private final WfTemplateMapper templateMapper;
    private final WfNodeMapper nodeMapper;
    private final WfNodeApproverMapper approverMapper;

    @GetMapping
    public Result<List<WorkflowTemplateVO>> list() {
        List<WfTemplate> templates = templateMapper.selectList(Wrappers.lambdaQuery(WfTemplate.class)
                .eq(WfTemplate::getDeleted, 0)
                .orderByAsc(WfTemplate::getId));
        return Results.success(templates.stream().map(template -> toVO(template, false)).toList());
    }

    @GetMapping("/{id}")
    public Result<WorkflowTemplateVO> detail(@PathVariable Long id) {
        WfTemplate template = templateMapper.selectById(id);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流模板不存在");
        }
        return Results.success(toVO(template, true));
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
