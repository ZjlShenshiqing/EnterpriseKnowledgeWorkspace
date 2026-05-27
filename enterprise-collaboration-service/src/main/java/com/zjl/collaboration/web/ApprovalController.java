package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysApprovalRequest;
import com.zjl.collaboration.entity.SysApprovalRecord;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysApprovalRequestMapper;
import com.zjl.collaboration.mapper.SysApprovalRecordMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final SysApprovalRequestMapper requestMapper;
    private final SysApprovalRecordMapper recordMapper;
    private final GatewayUserClient gatewayUserClient;

    @GetMapping
    public Result<List<SysApprovalRequest>> list(@RequestHeader("X-User-Id") Long userId, @RequestHeader("X-Is-Admin") String isAdmin) {
        if ("true".equals(isAdmin)) return Results.success(requestMapper.selectList(Wrappers.lambdaQuery(SysApprovalRequest.class).orderByDesc(SysApprovalRequest::getCreatedAt)));
        return Results.success(requestMapper.selectList(Wrappers.lambdaQuery(SysApprovalRequest.class).eq(SysApprovalRequest::getUserId, userId).orderByDesc(SysApprovalRequest::getCreatedAt)));
    }

    @PostMapping
    public Result<Long> create(@RequestBody Map<String,Object> body, @RequestHeader("X-User-Id") Long userId) {
        UserInfo user = gatewayUserClient.getById(userId);
        SysApprovalRequest r = new SysApprovalRequest();
        r.setType(body.get("type").toString()); r.setUserId(userId);
        r.setUserName(user != null ? user.realName() : null); r.setTitle(body.get("title").toString());
        r.setFormData(body.get("formData") != null ? body.get("formData").toString() : "{}");
        r.setStatus("pending"); r.setCreatedAt(LocalDateTime.now());
        requestMapper.insert(r);
        log.info("审批创建: userId={}, requestId={}", userId, r.getId());
        return Results.success(r.getId());
    }

    @GetMapping("/{id}")
    public Result<Map<String,Object>> detail(@PathVariable Long id) {
        SysApprovalRequest req = requestMapper.selectById(id);
        List<SysApprovalRecord> records = recordMapper.selectList(Wrappers.lambdaQuery(SysApprovalRecord.class).eq(SysApprovalRecord::getRequestId, id).orderByAsc(SysApprovalRecord::getCreatedAt));
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", req.getId()); m.put("type", req.getType()); m.put("title", req.getTitle()); m.put("userName", req.getUserName()); m.put("status", req.getStatus()); m.put("createdAt", req.getCreatedAt()); m.put("records", records);
        return Results.success(m);
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody Map<String,String> body, @RequestHeader("X-User-Id") Long userId) {
        UserInfo user = gatewayUserClient.getById(userId);
        SysApprovalRecord rec = new SysApprovalRecord(); rec.setRequestId(id); rec.setApproverId(userId);
        rec.setApproverName(user != null ? user.realName() : null); rec.setAction(body.get("action"));
        rec.setComment(body.get("comment")); rec.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(rec);

        SysApprovalRequest req = requestMapper.selectById(id);
        String newStatus = "rejected".equals(body.get("action")) ? "rejected" : nextStatus(req);
        log.info("审批处理: requestId={}, userId={}, action={}, newStatus={}", id, userId, body.get("action"), newStatus);
        req.setStatus(newStatus); req.setUpdatedAt(LocalDateTime.now());
        requestMapper.updateById(req);
        return Results.success();
    }

    private String nextStatus(SysApprovalRequest req) {
        if ("leave".equals(req.getType())) {
            return switch (req.getStatus()) { case "pending"->"manager_approved"; case "manager_approved"->"approved"; default->req.getStatus(); };
        }
        return switch (req.getStatus()) { case "pending"->"manager_approved"; case "manager_approved"->"finance_approved"; case "finance_approved"->"approved"; default->req.getStatus(); };
    }
}
