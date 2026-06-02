package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.entity.WfTask;

import java.util.List;

public interface WorkflowTaskService {
    List<WfTask> listMyPendingTasks(Long userId);

    void requireCanHandle(WfTask task, Long userId);

    void closeOtherPendingTasks(Long instanceId, Long nodeId, Long handledTaskId, Long operatorId);
}
