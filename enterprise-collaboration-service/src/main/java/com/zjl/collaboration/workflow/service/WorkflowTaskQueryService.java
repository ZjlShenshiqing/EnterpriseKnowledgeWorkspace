package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.vo.WorkflowTaskVO;

import java.util.List;

/**
 * 工作流任务查询服务。
 */
public interface WorkflowTaskQueryService {

    /**
     * 查询我的待办任务。
     *
     * @param userId 用户 ID
     * @return 任务列表
     */
    List<WorkflowTaskVO> listMine(Long userId);
}
