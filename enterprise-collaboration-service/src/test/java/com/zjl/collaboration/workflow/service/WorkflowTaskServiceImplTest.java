package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.workflow.entity.WfTask;
import com.zjl.collaboration.workflow.enums.WfTaskStatus;
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.mapper.WfTaskMapper;
import com.zjl.collaboration.workflow.service.impl.WorkflowTaskServiceImpl;
import com.zjl.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTaskServiceImplTest {
    @Mock
    private WfTaskMapper taskMapper;

    @Mock
    private WfRecordMapper recordMapper;

    @Mock
    private GatewayUserClient gatewayUserClient;

    @InjectMocks
    private WorkflowTaskServiceImpl taskService;

    @Test
    void nonCandidateCannotHandleTask() {
        WfTask task = new WfTask();
        task.setAssigneeType("USER");
        task.setAssigneeId(7L);
        task.setStatus(WfTaskStatus.PENDING);

        assertThatThrownBy(() -> taskService.requireCanHandle(task, 6L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void roleCandidateCanHandleRoleTask() {
        WfTask task = new WfTask();
        task.setAssigneeType("ROLE");
        task.setAssigneeId(2L);
        task.setStatus(WfTaskStatus.PENDING);

        UserInfo user = new UserInfo(6L, "zhangsan", "张三", 1L, "研发部",
                List.of(new UserInfo.RoleInfo(2L, "manager", "部门主管")));
        when(gatewayUserClient.getById(6L)).thenReturn(user);

        assertThatCode(() -> taskService.requireCanHandle(task, 6L)).doesNotThrowAnyException();
    }
}
