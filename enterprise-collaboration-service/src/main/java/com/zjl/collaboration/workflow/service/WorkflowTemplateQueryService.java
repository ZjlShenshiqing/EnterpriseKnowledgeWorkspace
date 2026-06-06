package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.vo.WorkflowTemplateVO;

import java.util.List;

/**
 * 工作流模板查询服务。
 */
public interface WorkflowTemplateQueryService {

    /**
     * 查询模板列表。
     *
     * @return 模板列表
     */
    List<WorkflowTemplateVO> list();

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    WorkflowTemplateVO detail(Long id);
}
