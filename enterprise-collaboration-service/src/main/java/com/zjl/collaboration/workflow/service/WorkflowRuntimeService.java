package com.zjl.collaboration.workflow.service;

public interface WorkflowRuntimeService {
    Long startApproval(String approvalType, Long approvalId, Long starterId);

    void approveTask(Long taskId, Long operatorId, String comment);

    void rejectTask(Long taskId, Long operatorId, String comment);
}
