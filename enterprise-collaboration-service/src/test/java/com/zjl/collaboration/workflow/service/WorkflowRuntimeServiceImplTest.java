package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.entity.WfInstance;
import com.zjl.collaboration.workflow.entity.WfNode;
import com.zjl.collaboration.workflow.entity.WfNodeApprover;
import com.zjl.collaboration.workflow.entity.WfRecord;
import com.zjl.collaboration.workflow.entity.WfTask;
import com.zjl.collaboration.workflow.entity.WfTemplate;
import com.zjl.collaboration.workflow.enums.WfAction;
import com.zjl.collaboration.workflow.enums.WfBusinessType;
import com.zjl.collaboration.workflow.enums.WfInstanceStatus;
import com.zjl.collaboration.workflow.enums.WfNodeType;
import com.zjl.collaboration.workflow.enums.WfTaskStatus;
import com.zjl.collaboration.workflow.mapper.WfInstanceMapper;
import com.zjl.collaboration.workflow.mapper.WfNodeMapper;
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.mapper.WfTaskMapper;
import com.zjl.collaboration.workflow.service.impl.WorkflowRuntimeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRuntimeServiceImplTest {
    @Mock
    private WorkflowTemplateService templateService;

    @Mock
    private WfInstanceMapper instanceMapper;

    @Mock
    private WfTaskMapper taskMapper;

    @Mock
    private WfRecordMapper recordMapper;

    @Mock
    private WfNodeMapper nodeMapper;

    @Mock
    private WorkflowTaskService taskService;

    @InjectMocks
    private WorkflowRuntimeServiceImpl runtimeService;

    @Test
    void startApprovalCreatesRunningInstanceFirstTasksAndStartRecord() {
        WfTemplate template = new WfTemplate();
        template.setId(10L);
        template.setCode("leave");

        WfNode firstNode = new WfNode();
        firstNode.setId(20L);

        WfNodeApprover approver = new WfNodeApprover();
        approver.setApproverType("ROLE");
        approver.setApproverId(2L);

        when(templateService.requireEnabledTemplate(WfBusinessType.APPROVAL, "leave")).thenReturn(template);
        when(templateService.requireFirstApprovalNode(10L)).thenReturn(firstNode);
        when(templateService.requireApprovers(20L)).thenReturn(List.of(approver));
        when(instanceMapper.insert(any(WfInstance.class))).thenAnswer(invocation -> {
            WfInstance instance = invocation.getArgument(0);
            instance.setId(99L);
            return 1;
        });

        Long instanceId = runtimeService.startApproval("leave", 1001L, 6L);

        ArgumentCaptor<WfInstance> instanceCaptor = ArgumentCaptor.forClass(WfInstance.class);
        ArgumentCaptor<WfTask> taskCaptor = ArgumentCaptor.forClass(WfTask.class);
        ArgumentCaptor<WfRecord> recordCaptor = ArgumentCaptor.forClass(WfRecord.class);

        verify(instanceMapper).insert(instanceCaptor.capture());
        verify(taskMapper).insert(taskCaptor.capture());
        verify(recordMapper).insert(recordCaptor.capture());

        assertThat(instanceId).isEqualTo(99L);
        assertThat(instanceCaptor.getValue().getStatus()).isEqualTo(WfInstanceStatus.RUNNING);
        assertThat(instanceCaptor.getValue().getCurrentNodeId()).isEqualTo(20L);
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(WfTaskStatus.PENDING);
        assertThat(taskCaptor.getValue().getAssigneeType()).isEqualTo("ROLE");
        assertThat(taskCaptor.getValue().getAssigneeId()).isEqualTo(2L);
        assertThat(recordCaptor.getValue().getAction()).isEqualTo(WfAction.START);
    }

    @Test
    void approveTaskCreatesNextNodeTaskWhenNextApprovalNodeExists() {
        WfTask task = pendingTask(31L, 90L, 20L);
        WfInstance instance = runningInstance(90L, 10L, 20L);
        WfNode currentNode = approvalNode(20L, 10);
        WfNode nextNode = approvalNode(21L, 20);
        WfNodeApprover nextApprover = new WfNodeApprover();
        nextApprover.setApproverType("ROLE");
        nextApprover.setApproverId(3L);

        when(taskMapper.selectById(31L)).thenReturn(task);
        when(instanceMapper.selectById(90L)).thenReturn(instance);
        when(nodeMapper.selectById(20L)).thenReturn(currentNode);
        when(templateService.findNextApprovalNode(10L, 10)).thenReturn(nextNode);
        when(templateService.requireApprovers(21L)).thenReturn(List.of(nextApprover));

        runtimeService.approveTask(31L, 6L, "同意");

        ArgumentCaptor<WfTask> updatedTaskCaptor = ArgumentCaptor.forClass(WfTask.class);
        ArgumentCaptor<WfTask> insertedTaskCaptor = ArgumentCaptor.forClass(WfTask.class);
        ArgumentCaptor<WfInstance> instanceCaptor = ArgumentCaptor.forClass(WfInstance.class);
        verify(taskMapper).updateById(updatedTaskCaptor.capture());
        verify(taskMapper).insert(insertedTaskCaptor.capture());
        verify(instanceMapper).updateById(instanceCaptor.capture());
        verify(taskService).closeOtherPendingTasks(90L, 20L, 31L, 6L);

        assertThat(updatedTaskCaptor.getValue().getId()).isEqualTo(31L);
        assertThat(updatedTaskCaptor.getValue().getStatus()).isEqualTo(WfTaskStatus.APPROVED);
        assertThat(insertedTaskCaptor.getValue().getInstanceId()).isEqualTo(90L);
        assertThat(insertedTaskCaptor.getValue().getNodeId()).isEqualTo(21L);
        assertThat(insertedTaskCaptor.getValue().getStatus()).isEqualTo(WfTaskStatus.PENDING);
        assertThat(instanceCaptor.getValue().getStatus()).isEqualTo(WfInstanceStatus.RUNNING);
        assertThat(instanceCaptor.getValue().getCurrentNodeId()).isEqualTo(21L);
    }

    @Test
    void approveTaskCompletesWorkflowWhenNoNextApprovalNodeExists() {
        WfTask task = pendingTask(31L, 90L, 20L);
        WfInstance instance = runningInstance(90L, 10L, 20L);
        WfNode currentNode = approvalNode(20L, 10);

        when(taskMapper.selectById(31L)).thenReturn(task);
        when(instanceMapper.selectById(90L)).thenReturn(instance);
        when(nodeMapper.selectById(20L)).thenReturn(currentNode);
        when(templateService.findNextApprovalNode(10L, 10)).thenReturn(null);

        runtimeService.approveTask(31L, 6L, "同意");

        ArgumentCaptor<WfInstance> instanceCaptor = ArgumentCaptor.forClass(WfInstance.class);
        ArgumentCaptor<WfRecord> recordCaptor = ArgumentCaptor.forClass(WfRecord.class);
        verify(instanceMapper).updateById(instanceCaptor.capture());
        verify(recordMapper, atLeastOnce()).insert(recordCaptor.capture());

        assertThat(instanceCaptor.getValue().getStatus()).isEqualTo(WfInstanceStatus.APPROVED);
        assertThat(recordCaptor.getAllValues()).anySatisfy(record ->
                assertThat(record.getAction()).isEqualTo(WfAction.COMPLETE));
    }

    @Test
    void rejectTaskEndsWorkflowAndClosesSameNodePendingTasks() {
        WfTask task = pendingTask(31L, 90L, 20L);
        WfInstance instance = runningInstance(90L, 10L, 20L);

        when(taskMapper.selectById(31L)).thenReturn(task);
        when(instanceMapper.selectById(90L)).thenReturn(instance);

        runtimeService.rejectTask(31L, 6L, "不同意");

        ArgumentCaptor<WfTask> taskCaptor = ArgumentCaptor.forClass(WfTask.class);
        ArgumentCaptor<WfInstance> instanceCaptor = ArgumentCaptor.forClass(WfInstance.class);
        ArgumentCaptor<WfRecord> recordCaptor = ArgumentCaptor.forClass(WfRecord.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        verify(instanceMapper).updateById(instanceCaptor.capture());
        verify(recordMapper).insert(recordCaptor.capture());
        verify(taskService).closeOtherPendingTasks(90L, 20L, 31L, 6L);

        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(WfTaskStatus.REJECTED);
        assertThat(instanceCaptor.getValue().getStatus()).isEqualTo(WfInstanceStatus.REJECTED);
        assertThat(recordCaptor.getValue().getAction()).isEqualTo(WfAction.REJECT);
    }

    private static WfTask pendingTask(Long id, Long instanceId, Long nodeId) {
        WfTask task = new WfTask();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setNodeId(nodeId);
        task.setStatus(WfTaskStatus.PENDING);
        return task;
    }

    private static WfInstance runningInstance(Long id, Long templateId, Long currentNodeId) {
        WfInstance instance = new WfInstance();
        instance.setId(id);
        instance.setTemplateId(templateId);
        instance.setStatus(WfInstanceStatus.RUNNING);
        instance.setCurrentNodeId(currentNodeId);
        return instance;
    }

    private static WfNode approvalNode(Long id, Integer sortOrder) {
        WfNode node = new WfNode();
        node.setId(id);
        node.setNodeType(WfNodeType.APPROVAL);
        node.setSortOrder(sortOrder);
        return node;
    }
}
