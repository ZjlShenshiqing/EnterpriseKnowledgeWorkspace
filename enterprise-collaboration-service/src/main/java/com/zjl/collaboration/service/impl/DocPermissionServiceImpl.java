package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.entity.SysDocCollaborator;
import com.zjl.collaboration.entity.SysDocShareLink;
import com.zjl.collaboration.mapper.SysDocCollaboratorMapper;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysDocShareLinkMapper;
import com.zjl.collaboration.service.DocPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocPermissionServiceImpl implements DocPermissionService {

    private final SysDocMapper docMapper;
    private final SysDocCollaboratorMapper collaboratorMapper;
    private final SysDocShareLinkMapper shareLinkMapper;

    @Override
    public Permission checkPermission(Long docId, Long userId, Long deptId) {
        SysDoc doc = docMapper.selectById(docId);
        if (doc == null) return null;

        if (userId != null && userId.equals(doc.getUpdatedBy())) {
            return Permission.EDIT;
        }

        Long count = collaboratorMapper.selectCount(new LambdaQueryWrapper<SysDocCollaborator>()
            .eq(SysDocCollaborator::getDocId, docId)
            .eq(SysDocCollaborator::getDeleted, 0)
            .eq(SysDocCollaborator::getTargetType, "USER")
            .eq(SysDocCollaborator::getTargetId, userId));
        if (count != null && count > 0) {
            SysDocCollaborator collab = collaboratorMapper.selectOne(new LambdaQueryWrapper<SysDocCollaborator>()
                .eq(SysDocCollaborator::getDocId, docId)
                .eq(SysDocCollaborator::getDeleted, 0)
                .eq(SysDocCollaborator::getTargetType, "USER")
                .eq(SysDocCollaborator::getTargetId, userId)
                .last("LIMIT 1"));
            return Permission.valueOf(collab.getPermission());
        }

        if (deptId != null) {
            Long deptCount = collaboratorMapper.selectCount(new LambdaQueryWrapper<SysDocCollaborator>()
                .eq(SysDocCollaborator::getDocId, docId)
                .eq(SysDocCollaborator::getDeleted, 0)
                .eq(SysDocCollaborator::getTargetType, "DEPT")
                .eq(SysDocCollaborator::getTargetId, deptId));
            if (deptCount != null && deptCount > 0) {
                SysDocCollaborator deptCollab = collaboratorMapper.selectOne(new LambdaQueryWrapper<SysDocCollaborator>()
                    .eq(SysDocCollaborator::getDocId, docId)
                    .eq(SysDocCollaborator::getDeleted, 0)
                    .eq(SysDocCollaborator::getTargetType, "DEPT")
                    .eq(SysDocCollaborator::getTargetId, deptId)
                    .last("LIMIT 1"));
                return Permission.valueOf(deptCollab.getPermission());
            }
        }

        log.warn("文档权限拒绝: docId={}, userId={}", docId, userId);
        return null;
    }

    @Override
    public Permission checkShareToken(String token) {
        SysDocShareLink link = shareLinkMapper.selectOne(new LambdaQueryWrapper<SysDocShareLink>()
            .eq(SysDocShareLink::getToken, token)
            .eq(SysDocShareLink::getDeleted, 0));
        if (link == null) return null;
        if (link.getExpiredAt() != null && link.getExpiredAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return Permission.valueOf(link.getPermission());
    }
}
