package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysTask;
import com.zjl.collaboration.entity.SysTaskComment;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysTaskCommentMapper;
import com.zjl.collaboration.mapper.SysTaskMapper;
import com.zjl.collaboration.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final SysTaskMapper taskMapper;
    private final SysTaskCommentMapper commentMapper;
    private final GatewayUserClient gatewayUserClient;

    @Override
    public List<Map<String, Object>> list(String status) {
        var query = Wrappers.lambdaQuery(SysTask.class).orderByDesc(SysTask::getCreatedAt);
        if (status != null) {
            query.eq(SysTask::getStatus, status);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysTask task : taskMapper.selectList(query)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.getId());
            item.put("title", task.getTitle());
            item.put("description", task.getDescription());
            item.put("priority", task.getPriority());
            item.put("status", task.getStatus());
            item.put("due_date", task.getDueDate());
            item.put("created_at", task.getCreatedAt());
            item.put("assignee_name", resolveAssigneeName(task.getAssigneeId()));
            result.add(item);
        }
        return result;
    }

    @Override
    public Long create(String title, String description, Long assigneeId, String priority, Long userId) {
        SysTask task = new SysTask();
        task.setTitle(title);
        task.setDescription(description);
        task.setCreatorId(userId);
        task.setAssigneeId(assigneeId);
        task.setPriority(priority);
        task.setStatus("todo");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("任务创建: userId={}, taskId={}", userId, task.getId());
        return task.getId();
    }

    @Override
    public void update(Long id, String title, String description, Long assigneeId, String priority) {
        SysTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        task.setTitle(title);
        task.setDescription(description);
        task.setAssigneeId(assigneeId);
        task.setPriority(priority);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    public void updateStatus(Long id, String status) {
        SysTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        String oldStatus = task.getStatus();
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务状态变更: taskId={}, from={}, to={}", id, oldStatus, status);
    }

    @Override
    public void delete(Long id) {
        SysTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        taskMapper.deleteById(id);
    }

    @Override
    public List<SysTaskComment> comments(Long taskId) {
        return commentMapper.selectList(Wrappers.lambdaQuery(SysTaskComment.class)
                .eq(SysTaskComment::getTaskId, taskId)
                .orderByAsc(SysTaskComment::getCreatedAt));
    }

    @Override
    public void addComment(Long taskId, String content, Long userId) {
        SysTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        UserInfo user = gatewayUserClient.getById(userId);
        SysTaskComment comment = new SysTaskComment();
        comment.setTaskId(taskId);
        comment.setUserId(userId);
        comment.setUserName(user != null ? user.realName() : null);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
    }

    private String resolveAssigneeName(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        UserInfo user = gatewayUserClient.getById(assigneeId);
        return user != null ? user.realName() : null;
    }
}
