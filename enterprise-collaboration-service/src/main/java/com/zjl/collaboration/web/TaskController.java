package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final SysTaskMapper taskMapper;
    private final SysTaskCommentMapper commentMapper;
    private final SysUserMapper userMapper;

    @GetMapping
    public Result<List<Map<String,Object>>> list(@RequestParam(required=false) String status) {
        var q = Wrappers.lambdaQuery(SysTask.class).orderByDesc(SysTask::getCreatedAt);
        if (status != null) q.eq(SysTask::getStatus, status);
        List<SysTask> tasks = taskMapper.selectList(q);
        List<Map<String,Object>> result = new ArrayList<>();
        for (SysTask t : tasks) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",t.getId()); m.put("title",t.getTitle()); m.put("description",t.getDescription());
            m.put("priority",t.getPriority()); m.put("status",t.getStatus()); m.put("due_date",t.getDueDate());
            m.put("created_at",t.getCreatedAt());
            if (t.getAssigneeId() != null) { SysUser u = userMapper.selectById(t.getAssigneeId()); m.put("assignee_name", u!=null?u.getRealName():null); }
            else m.put("assignee_name", null);
            result.add(m);
        }
        return Results.success(result);
    }

    @PostMapping
    public Result<Long> create(@RequestBody TaskReq req, @RequestHeader("X-User-Id") Long userId) {
        SysTask t = new SysTask(); t.setTitle(req.getTitle()); t.setDescription(req.getDescription());
        t.setCreatorId(userId); t.setAssigneeId(req.getAssigneeId()); t.setPriority(req.getPriority());
        t.setStatus("todo"); t.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(t);
        log.info("任务创建: userId={}, taskId={}", userId, t.getId());
        return Results.success(t.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskReq req) {
        SysTask t = taskMapper.selectById(id);
        if (t==null) return Results.success();
        t.setTitle(req.getTitle()); t.setDescription(req.getDescription()); t.setAssigneeId(req.getAssigneeId());
        t.setPriority(req.getPriority()); t.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        return Results.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String,String> body) {
        SysTask t = taskMapper.selectById(id);
        if (t==null) return Results.success();
        String oldStatus = t.getStatus();
        t.setStatus(body.get("status")); t.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        log.info("任务状态变更: taskId={}, from={}, to={}", id, oldStatus, body.get("status"));
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { taskMapper.deleteById(id); return Results.success(); }

    @GetMapping("/{taskId}/comments")
    public Result<List<SysTaskComment>> comments(@PathVariable Long taskId) {
        return Results.success(commentMapper.selectList(Wrappers.lambdaQuery(SysTaskComment.class).eq(SysTaskComment::getTaskId, taskId).orderByAsc(SysTaskComment::getCreatedAt)));
    }

    @PostMapping("/{taskId}/comments")
    public Result<Void> addComment(@PathVariable Long taskId, @RequestBody Map<String,String> body, @RequestHeader("X-User-Id") Long userId) {
        SysUser user = userMapper.selectById(userId);
        SysTaskComment c = new SysTaskComment(); c.setTaskId(taskId); c.setUserId(userId);
        c.setUserName(user!=null?user.getRealName():null); c.setContent(body.get("content")); c.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        return Results.success();
    }

    @Data public static class TaskReq { private String title; private String description; private Long assigneeId; private String priority; }
}
