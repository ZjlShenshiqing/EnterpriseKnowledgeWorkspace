package com.zjl.collaboration.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.workflow.entity.WfRecord;
import com.zjl.collaboration.workflow.entity.WfTask;
import com.zjl.collaboration.workflow.enums.WfAction;
import com.zjl.collaboration.workflow.enums.WfTaskStatus;
import com.zjl.collaboration.workflow.mapper.WfRecordMapper;
import com.zjl.collaboration.workflow.mapper.WfTaskMapper;
import com.zjl.collaboration.workflow.service.WorkflowTaskService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkflowTaskServiceImpl implements WorkflowTaskService {
    private static final String ASSIGNEE_USER = "USER";
    private static final String ASSIGNEE_ROLE = "ROLE";

    private final WfTaskMapper taskMapper;
    private final WfRecordMapper recordMapper;
    private final GatewayUserClient gatewayUserClient;

    @Override
    public List<WfTask> listMyPendingTasks(Long userId) {
        List<Long> roleIds = getUserRoleIds(userId);
        return taskMapper.selectList(Wrappers.lambdaQuery(WfTask.class)
                .eq(WfTask::getStatus, WfTaskStatus.PENDING)
                .eq(WfTask::getDeleted, 0)
                .and(wrapper -> {
                    wrapper.eq(WfTask::getAssigneeType, ASSIGNEE_USER)
                            .eq(WfTask::getAssigneeId, userId);
                    if (!roleIds.isEmpty()) {
                        wrapper.or(roleWrapper -> roleWrapper.eq(WfTask::getAssigneeType, ASSIGNEE_ROLE)
                                .in(WfTask::getAssigneeId, roleIds));
                    }
                }));
    }

    @Override
    public void requireCanHandle(WfTask task, Long userId) {
        if (task == null || !Objects.equals(WfTaskStatus.PENDING, task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批任务不可处理");
        }
        if (Objects.equals(ASSIGNEE_USER, task.getAssigneeType())
                && Objects.equals(task.getAssigneeId(), userId)) {
            return;
        }
        if (Objects.equals(ASSIGNEE_ROLE, task.getAssigneeType()) && hasRole(userId, task.getAssigneeId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权限处理该审批任务");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOtherPendingTasks(Long instanceId, Long nodeId, Long handledTaskId, Long operatorId) {
        List<WfTask> tasks = taskMapper.selectList(Wrappers.lambdaQuery(WfTask.class)
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getNodeId, nodeId)
                .eq(WfTask::getStatus, WfTaskStatus.PENDING)
                .eq(WfTask::getDeleted, 0)
                .ne(WfTask::getId, handledTaskId));
        LocalDateTime now = LocalDateTime.now();
        for (WfTask task : tasks) {
            task.setStatus(WfTaskStatus.CLOSED);
            task.setClaimedBy(operatorId);
            task.setHandledAt(now);
            taskMapper.updateById(task);

            WfRecord record = new WfRecord();
            record.setInstanceId(instanceId);
            record.setNodeId(nodeId);
            record.setTaskId(task.getId());
            record.setOperatorId(operatorId);
            record.setAction(WfAction.AUTO_CLOSE);
            record.setFromStatus(WfTaskStatus.PENDING);
            record.setToStatus(WfTaskStatus.CLOSED);
            record.setCreatedAt(now);
            recordMapper.insert(record);
        }
    }

    private boolean hasRole(Long userId, Long roleId) {
        UserInfo user = gatewayUserClient.getById(userId);
        if (user == null || user.roles() == null) {
            return false;
        }
        return user.roles().stream()
                .anyMatch(role -> Objects.equals(role.id(), roleId)
                        || Objects.equals(role.code(), String.valueOf(roleId)));
    }

    private List<Long> getUserRoleIds(Long userId) {
        UserInfo user = gatewayUserClient.getById(userId);
        if (user == null || user.roles() == null) {
            return List.of();
        }
        return user.roles().stream()
                .map(UserInfo.RoleInfo::id)
                .filter(Objects::nonNull)
                .toList();
    }
}
