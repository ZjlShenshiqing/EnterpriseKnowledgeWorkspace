package com.zjl.collaboration.workflow.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zjl.collaboration.entity.SysApprovalRequest;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysApprovalRequestMapper;
import com.zjl.collaboration.workflow.dto.ApprovalCreateRequest;
import com.zjl.collaboration.workflow.entity.WfInstance;
import com.zjl.collaboration.workflow.entity.WfRecord;
import com.zjl.collaboration.workflow.mapper.WfInstanceMapper;
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.service.impl.ApprovalApplicationServiceImpl;
import com.zjl.collaboration.workflow.vo.ApprovalCreateVO;
import com.zjl.collaboration.workflow.vo.ApprovalDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalApplicationServiceImplTest {
    @Mock
    private SysApprovalRequestMapper requestMapper;

    @Mock
    private WfInstanceMapper instanceMapper;

    @Mock
    private WfRecordMapper recordMapper;

    @Mock
    private WorkflowRuntimeService runtimeService;

    @Mock
    private GatewayUserClient gatewayUserClient;

    @InjectMocks
    private ApprovalApplicationServiceImpl approvalApplicationService;

    @Test
    void createApprovalCallsWorkflowRuntimeAndStoresWorkflowInstanceId() {
        ApprovalCreateRequest request = new ApprovalCreateRequest();
        request.setType("leave");
        request.setTitle("请假");
        request.setFormData(Map.of("days", 1));

        when(gatewayUserClient.getById(6L)).thenReturn(new UserInfo(6L, "zhangsan", "张三", 1L, "研发部"));
        when(requestMapper.insert(any(SysApprovalRequest.class))).thenAnswer(invocation -> {
            SysApprovalRequest approval = invocation.getArgument(0);
            approval.setId(1001L);
            return 1;
        });
        when(runtimeService.startApproval("leave", 1001L, 6L)).thenReturn(9001L);

        ApprovalCreateVO created = approvalApplicationService.create(request, 6L);

        ArgumentCaptor<SysApprovalRequest> approvalCaptor = ArgumentCaptor.forClass(SysApprovalRequest.class);
        verify(requestMapper).insert(approvalCaptor.capture());
        verify(runtimeService).startApproval("leave", 1001L, 6L);
        verify(requestMapper).updateById(any(SysApprovalRequest.class));

        assertThat(created.getApprovalId()).isEqualTo(1001L);
        assertThat(created.getWorkflowInstanceId()).isEqualTo(9001L);
        assertThat(approvalCaptor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(approvalCaptor.getValue().getFormData()).contains("\"days\":1");
    }

    @Test
    void listMineReturnsOnlyCurrentUsersRecords() {
        SysApprovalRequest approval = new SysApprovalRequest();
        approval.setId(1001L);
        approval.setUserId(6L);
        approval.setTitle("请假");
        when(requestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(approval));

        assertThat(approvalApplicationService.listMine(6L))
                .hasSize(1)
                .first()
                .extracting("id", "userId", "title")
                .containsExactly(1001L, 6L, "请假");
    }

    @Test
    void detailReturnsApplicationInstanceAndRecords() {
        SysApprovalRequest approval = new SysApprovalRequest();
        approval.setId(1001L);
        approval.setUserId(6L);
        approval.setWorkflowInstanceId(9001L);
        when(requestMapper.selectById(1001L)).thenReturn(approval);

        WfInstance instance = new WfInstance();
        instance.setId(9001L);
        when(instanceMapper.selectById(9001L)).thenReturn(instance);

        WfRecord record = new WfRecord();
        record.setId(1L);
        record.setInstanceId(9001L);
        record.setAction("START");
        when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record));

        ApprovalDetailVO detail = approvalApplicationService.detail(1001L, 6L, false);

        assertThat(detail.getId()).isEqualTo(1001L);
        assertThat(detail.getInstance().getId()).isEqualTo(9001L);
        assertThat(detail.getRecords()).hasSize(1);
        assertThat(detail.getRecords().get(0).getAction()).isEqualTo("START");
    }
}
