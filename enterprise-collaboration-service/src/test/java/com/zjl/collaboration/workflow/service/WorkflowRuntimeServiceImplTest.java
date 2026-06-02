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
import com.zjl.collaboration.workflow.enums.WfTaskStatus;
import com.zjl.collaboration.workflow.mapper.WfInstanceMapper;
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
}
