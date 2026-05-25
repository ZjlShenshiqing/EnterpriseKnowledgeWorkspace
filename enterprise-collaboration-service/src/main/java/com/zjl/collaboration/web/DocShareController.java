package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocPermissionService;
import com.zjl.collaboration.service.DocPresenceService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocShareController {

    private final SysDocShareLinkMapper shareLinkMapper;
    private final SysDocCollaboratorMapper collaboratorMapper;
    private final SysDocMapper docMapper;
    private final SysUserMapper userMapper;
    private final DocPermissionService permissionService;
    private final DocOTService docOTService;
    private final DocPresenceService presenceService;

    @GetMapping("/docs/{docId}/collaborators")
    public Result<List<Map<String, Object>>> listCollaborators(@PathVariable Long docId) {
        List<SysDocCollaborator> list = collaboratorMapper.selectList(
                new LambdaQueryWrapper<SysDocCollaborator>()
                        .eq(SysDocCollaborator::getDocId, docId)
                        .eq(SysDocCollaborator::getDeleted, 0));

        Set<Long> userIds = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDocCollaborator c : list) {
            if ("USER".equals(c.getTargetType())) {
                userIds.add(c.getTargetId());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("docId", c.getDocId());
            m.put("targetType", c.getTargetType());
            m.put("targetId", c.getTargetId());
            m.put("permission", c.getPermission());
            m.put("online", false);
            result.add(m);
        }

        if (!userIds.isEmpty()) {
            var users = userMapper.selectBatchIds(userIds);
            Map<Long, String> nameMap = users.stream()
                    .collect(Collectors.toMap(u -> u.getId(), u -> u.getRealName(), (a, b) -> a));
            for (Map<String, Object> m : result) {
                if ("USER".equals(m.get("targetType"))) {
                    m.put("targetName", nameMap.getOrDefault(m.get("targetId"), ""));
                }
            }
        }

        return Results.success(result);
    }

    @PostMapping("/docs/{docId}/collaborators")
    public Result<Map<String, Object>> addCollaborator(@PathVariable Long docId,
                                                        @RequestBody CollaboratorReq req) {
        log.info("协作者添加: docId={}, targetId={}, permission={}", docId, req.getTargetId(), req.getPermission());
        SysDocCollaborator c = new SysDocCollaborator();
        c.setDocId(docId);
        c.setTargetType(req.getTargetType());
        c.setTargetId(req.getTargetId());
        c.setPermission(req.getPermission());
        c.setDeleted(0);
        c.setCreatedAt(LocalDateTime.now());
        collaboratorMapper.insert(c);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        return Results.success(m);
    }

    @PutMapping("/collaborators/{id}")
    public Result<Void> updateCollaborator(@PathVariable Long id,
                                            @RequestBody CollaboratorReq req) {
        SysDocCollaborator c = collaboratorMapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND, "协作者不存在");
        if (req.getPermission() != null) c.setPermission(req.getPermission());
        collaboratorMapper.updateById(c);
        return Results.success();
    }

    @DeleteMapping("/collaborators/{id}")
    public Result<Void> removeCollaborator(@PathVariable Long id) {
        SysDocCollaborator c = collaboratorMapper.selectById(id);
        if (c != null) {
            c.setDeleted(1);
            collaboratorMapper.updateById(c);
        }
        return Results.success();
    }

    @GetMapping("/docs/{docId}/shares")
    public Result<List<Map<String, Object>>> listShares(@PathVariable Long docId) {
        List<SysDocShareLink> list = shareLinkMapper.selectList(
                new LambdaQueryWrapper<SysDocShareLink>()
                        .eq(SysDocShareLink::getDocId, docId)
                        .eq(SysDocShareLink::getDeleted, 0)
                        .orderByDesc(SysDocShareLink::getCreatedAt));

        List<Map<String, Object>> result = list.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("token", s.getToken());
            m.put("permission", s.getPermission());
            m.put("expiredAt", s.getExpiredAt());
            m.put("createdAt", s.getCreatedAt());
            return m;
        }).toList();
        return Results.success(result);
    }

    @PostMapping("/docs/{docId}/shares")
    public Result<Map<String, Object>> createShare(@PathVariable Long docId,
                                                    @RequestBody ShareReq req) {
        log.info("分享链接创建: docId={}, permission={}", docId, req.getPermission());
        SysDocShareLink link = new SysDocShareLink();
        link.setDocId(docId);
        link.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        link.setPermission(req.getPermission());
        link.setExpiredAt(req.getExpiredAt());
        link.setDeleted(0);
        link.setCreatedAt(LocalDateTime.now());
        shareLinkMapper.insert(link);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", link.getId());
        m.put("token", link.getToken());
        return Results.success(m);
    }

    @DeleteMapping("/shares/{id}")
    public Result<Void> deleteShare(@PathVariable Long id) {
        SysDocShareLink link = shareLinkMapper.selectById(id);
        if (link != null) {
            link.setDeleted(1);
            shareLinkMapper.updateById(link);
        }
        return Results.success();
    }

    @GetMapping("/docs/shared/{token}")
    public Result<Map<String, Object>> openByToken(@PathVariable String token) {
        DocPermissionService.Permission perm = permissionService.checkShareToken(token);
        if (perm == null) throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效或已过期");

        SysDocShareLink link = shareLinkMapper.selectOne(
                new LambdaQueryWrapper<SysDocShareLink>()
                        .eq(SysDocShareLink::getToken, token)
                        .eq(SysDocShareLink::getDeleted, 0));

        DocOTService.DocSnapshot snapshot = docOTService.getDocument(link.getDocId());
        if (snapshot == null) throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");

        SysDoc doc = docMapper.selectById(link.getDocId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("content", snapshot.content());
        m.put("version", snapshot.version());
        m.put("permission", perm.name());
        m.put("updatedByName", doc.getUpdatedByName());
        m.put("updatedAt", doc.getUpdatedAt());
        return Results.success(m);
    }

    @Data
    static class CollaboratorReq {
        private String targetType;
        private Long targetId;
        private String permission;
    }

    @Data
    static class ShareReq {
        private String permission;
        private LocalDateTime expiredAt;
    }
}
