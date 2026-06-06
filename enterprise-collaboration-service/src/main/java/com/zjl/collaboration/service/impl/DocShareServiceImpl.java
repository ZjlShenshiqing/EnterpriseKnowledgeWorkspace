package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.entity.SysDocCollaborator;
import com.zjl.collaboration.entity.SysDocShareLink;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.mapper.SysDocCollaboratorMapper;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysDocShareLinkMapper;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocPermissionService;
import com.zjl.collaboration.service.DocShareService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档分享业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocShareServiceImpl implements DocShareService {

    private final SysDocShareLinkMapper shareLinkMapper;
    private final SysDocCollaboratorMapper collaboratorMapper;
    private final SysDocMapper docMapper;
    private final GatewayUserClient gatewayUserClient;
    private final DocPermissionService permissionService;
    private final DocOTService docOTService;

    @Override
    public List<Map<String, Object>> listCollaborators(Long docId) {
        List<SysDocCollaborator> collaborators = collaboratorMapper.selectList(
                new LambdaQueryWrapper<SysDocCollaborator>()
                        .eq(SysDocCollaborator::getDocId, docId)
                        .eq(SysDocCollaborator::getDeleted, 0));
        Set<Long> userIds = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDocCollaborator collaborator : collaborators) {
            if ("USER".equals(collaborator.getTargetType())) {
                userIds.add(collaborator.getTargetId());
            }
            result.add(collaboratorToMap(collaborator));
        }
        fillCollaboratorNames(result, userIds);
        return result;
    }

    @Override
    public Map<String, Object> addCollaborator(Long docId, String targetType, Long targetId, String permission) {
        log.info("协作者添加: docId={}, targetId={}, permission={}", docId, targetId, permission);
        SysDocCollaborator collaborator = new SysDocCollaborator();
        collaborator.setDocId(docId);
        collaborator.setTargetType(targetType);
        collaborator.setTargetId(targetId);
        collaborator.setPermission(permission);
        collaborator.setDeleted(0);
        collaborator.setCreatedAt(LocalDateTime.now());
        collaboratorMapper.insert(collaborator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", collaborator.getId());
        return result;
    }

    @Override
    public void updateCollaborator(Long id, String permission) {
        SysDocCollaborator collaborator = collaboratorMapper.selectById(id);
        if (collaborator == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "协作者不存在");
        }
        if (permission != null) {
            collaborator.setPermission(permission);
        }
        collaboratorMapper.updateById(collaborator);
    }

    @Override
    public void removeCollaborator(Long id) {
        SysDocCollaborator collaborator = collaboratorMapper.selectById(id);
        if (collaborator != null) {
            collaborator.setDeleted(1);
            collaboratorMapper.updateById(collaborator);
        }
    }

    @Override
    public List<Map<String, Object>> listShares(Long docId) {
        return shareLinkMapper.selectList(
                        new LambdaQueryWrapper<SysDocShareLink>()
                                .eq(SysDocShareLink::getDocId, docId)
                                .eq(SysDocShareLink::getDeleted, 0)
                                .orderByDesc(SysDocShareLink::getCreatedAt))
                .stream()
                .map(this::shareToMap)
                .toList();
    }

    @Override
    public Map<String, Object> createShare(Long docId, String permission, LocalDateTime expiredAt) {
        log.info("分享链接创建: docId={}, permission={}", docId, permission);
        SysDocShareLink link = new SysDocShareLink();
        link.setDocId(docId);
        link.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        link.setPermission(permission);
        link.setExpiredAt(expiredAt);
        link.setDeleted(0);
        link.setCreatedAt(LocalDateTime.now());
        shareLinkMapper.insert(link);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", link.getId());
        result.put("token", link.getToken());
        return result;
    }

    @Override
    public void deleteShare(Long id) {
        SysDocShareLink link = shareLinkMapper.selectById(id);
        if (link != null) {
            link.setDeleted(1);
            shareLinkMapper.updateById(link);
        }
    }

    @Override
    public Map<String, Object> openByToken(String token) {
        DocPermissionService.Permission permission = permissionService.checkShareToken(token);
        if (permission == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效或已过期");
        }
        SysDocShareLink link = shareLinkMapper.selectOne(
                new LambdaQueryWrapper<SysDocShareLink>()
                        .eq(SysDocShareLink::getToken, token)
                        .eq(SysDocShareLink::getDeleted, 0));
        DocOTService.DocSnapshot snapshot = docOTService.getDocument(link.getDocId());
        if (snapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        SysDoc doc = docMapper.selectById(link.getDocId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", doc.getId());
        result.put("title", doc.getTitle());
        result.put("content", snapshot.content());
        result.put("version", snapshot.version());
        result.put("permission", permission.name());
        result.put("updatedByName", doc.getUpdatedByName());
        result.put("updatedAt", doc.getUpdatedAt());
        return result;
    }

    private void fillCollaboratorNames(List<Map<String, Object>> collaborators, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        gatewayUserClient.batchQuery(new ArrayList<>(userIds))
                .forEach((id, user) -> nameMap.put(id, user.realName()));
        for (Map<String, Object> collaborator : collaborators) {
            if ("USER".equals(collaborator.get("targetType"))) {
                collaborator.put("targetName", nameMap.getOrDefault(collaborator.get("targetId"), ""));
            }
        }
    }

    private Map<String, Object> collaboratorToMap(SysDocCollaborator collaborator) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", collaborator.getId());
        result.put("docId", collaborator.getDocId());
        result.put("targetType", collaborator.getTargetType());
        result.put("targetId", collaborator.getTargetId());
        result.put("permission", collaborator.getPermission());
        result.put("online", false);
        return result;
    }

    private Map<String, Object> shareToMap(SysDocShareLink link) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", link.getId());
        result.put("token", link.getToken());
        result.put("permission", link.getPermission());
        result.put("expiredAt", link.getExpiredAt());
        result.put("createdAt", link.getCreatedAt());
        return result;
    }
}
