package com.zjl.collaboration.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.SysApprovalRequest;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysApprovalRequestMapper;
import com.zjl.collaboration.workflow.dto.ApprovalCreateRequest;
import com.zjl.collaboration.workflow.entity.WfInstance;
import com.zjl.collaboration.workflow.entity.WfRecord;
import com.zjl.collaboration.workflow.mapper.WfInstanceMapper;
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.enums.WfInstanceStatus;
import com.zjl.collaboration.workflow.service.ApprovalApplicationService;
import com.zjl.collaboration.workflow.service.WorkflowRuntimeService;
import com.zjl.collaboration.workflow.vo.ApprovalCreateVO;
import com.zjl.collaboration.workflow.vo.ApprovalDetailVO;
import com.zjl.collaboration.workflow.vo.ApprovalListVO;
import com.zjl.collaboration.workflow.vo.WfRecordVO;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ApprovalApplicationServiceImpl implements ApprovalApplicationService {
    private static final String STATUS_PENDING = "PENDING";

    private final SysApprovalRequestMapper requestMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfRecordMapper recordMapper;
    private final WorkflowRuntimeService runtimeService;
    private final GatewayUserClient gatewayUserClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @GlobalTransactional(timeoutMills = 300000, name = "approval-create")
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCreateVO create(ApprovalCreateRequest request, Long userId) {
        validateCreate(request);
        LocalDateTime now = LocalDateTime.now();
        UserInfo user = gatewayUserClient.getById(userId);

        SysApprovalRequest approval = new SysApprovalRequest();
        approval.setType(request.getType());
        approval.setUserId(userId);
        approval.setUserName(user != null ? user.realName() : null);
        approval.setTitle(request.getTitle().trim());
        approval.setFormData(toJson(request.getFormData()));
        approval.setStatus(STATUS_PENDING);
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        approval.setDeleted(0);
        requestMapper.insert(approval);

        Long instanceId = runtimeService.startApproval(request.getType(), approval.getId(), userId);
        approval.setWorkflowInstanceId(instanceId);
        approval.setStatus(WfInstanceStatus.RUNNING);
        approval.setUpdatedAt(LocalDateTime.now());
        requestMapper.updateById(approval);

        ApprovalCreateVO vo = new ApprovalCreateVO();
        vo.setApprovalId(approval.getId());
        vo.setWorkflowInstanceId(instanceId);
        return vo;
    }

    @Override
    public List<ApprovalListVO> listMine(Long userId) {
        return requestMapper.selectList(Wrappers.lambdaQuery(SysApprovalRequest.class)
                        .eq(SysApprovalRequest::getUserId, userId)
                        .eq(SysApprovalRequest::getDeleted, 0)
                        .orderByDesc(SysApprovalRequest::getCreatedAt))
                .stream()
                .map(this::toListVO)
                .toList();
    }

    @Override
    public List<ApprovalListVO> listAll() {
        return requestMapper.selectList(Wrappers.lambdaQuery(SysApprovalRequest.class)
                        .eq(SysApprovalRequest::getDeleted, 0)
                        .orderByDesc(SysApprovalRequest::getCreatedAt))
                .stream()
                .map(this::toListVO)
                .toList();
    }

    @Override
    public ApprovalDetailVO detail(Long approvalId, Long userId, boolean admin) {
        SysApprovalRequest approval = requestMapper.selectById(approvalId);
        if (approval == null || Objects.equals(approval.getDeleted(), 1)) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批申请不存在");
        }
        if (!admin && !Objects.equals(approval.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限查看该审批申请");
        }

        WfInstance instance = approval.getWorkflowInstanceId() != null
                ? instanceMapper.selectById(approval.getWorkflowInstanceId())
                : null;
        List<WfRecordVO> records = approval.getWorkflowInstanceId() != null
                ? recordMapper.selectList(Wrappers.lambdaQuery(WfRecord.class)
                        .eq(WfRecord::getInstanceId, approval.getWorkflowInstanceId())
                        .orderByAsc(WfRecord::getCreatedAt))
                .stream()
                .map(this::toRecordVO)
                .toList()
                : List.of();

        ApprovalDetailVO vo = new ApprovalDetailVO();
        vo.setId(approval.getId());
        vo.setType(approval.getType());
        vo.setUserId(approval.getUserId());
        vo.setUserName(approval.getUserName());
        vo.setTitle(approval.getTitle());
        vo.setFormData(approval.getFormData());
        vo.setStatus(approval.getStatus());
        vo.setWorkflowInstanceId(approval.getWorkflowInstanceId());
        vo.setCreatedAt(approval.getCreatedAt());
        vo.setUpdatedAt(approval.getUpdatedAt());
        vo.setInstance(instance);
        vo.setRecords(records);
        return vo;
    }

    private void validateCreate(ApprovalCreateRequest request) {
        if (request == null || request.getType() == null
                || (!Objects.equals("leave", request.getType()) && !Objects.equals("expense", request.getType()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批类型仅支持 leave 或 expense");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批标题不能为空");
        }
    }

    private String toJson(Map<String, Object> formData) {
        try {
            return objectMapper.writeValueAsString(formData != null ? formData : Map.of());
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批表单数据无法序列化");
        }
    }

    private ApprovalListVO toListVO(SysApprovalRequest approval) {
        ApprovalListVO vo = new ApprovalListVO();
        vo.setId(approval.getId());
        vo.setType(approval.getType());
        vo.setUserId(approval.getUserId());
        vo.setUserName(approval.getUserName());
        vo.setTitle(approval.getTitle());
        vo.setStatus(approval.getStatus());
        vo.setWorkflowInstanceId(approval.getWorkflowInstanceId());
        vo.setCreatedAt(approval.getCreatedAt());
        vo.setUpdatedAt(approval.getUpdatedAt());
        return vo;
    }

    private WfRecordVO toRecordVO(WfRecord record) {
        WfRecordVO vo = new WfRecordVO();
        vo.setId(record.getId());
        vo.setInstanceId(record.getInstanceId());
        vo.setNodeId(record.getNodeId());
        vo.setTaskId(record.getTaskId());
        vo.setOperatorId(record.getOperatorId());
        vo.setAction(record.getAction());
        vo.setFromStatus(record.getFromStatus());
        vo.setToStatus(record.getToStatus());
        vo.setComment(record.getComment());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
