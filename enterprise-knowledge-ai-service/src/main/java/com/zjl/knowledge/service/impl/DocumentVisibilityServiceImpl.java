package com.zjl.knowledge.service.impl;

import com.zjl.knowledge.domain.DocumentPermissionType;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.web.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DocumentVisibilityServiceImpl implements DocumentVisibilityService {

    @Override
    public boolean canView(KbDocument doc, UserContext user, List<KbDocumentPermission> permissions) {
        if (user.isAdmin()) {
            return true;
        }
        if (Objects.equals(doc.getOwnerId(), user.getUserId())) {
            return true;
        }
        final DocumentPermissionType type;
        try {
            type = DocumentPermissionType.valueOf(doc.getPermissionType());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return switch (type) {
            case ALL -> true;
            case DEPARTMENT -> doc.getDepartmentId() != null
                    && Objects.equals(doc.getDepartmentId(), user.getDepartmentId());
            case ADMIN -> false;
            case PROJECT -> user.getProjectId() != null && permissions.stream().anyMatch(p ->
                    "PROJECT".equals(p.getPermissionTargetType())
                            && Objects.equals(p.getPermissionTargetId(), user.getProjectId()));
            case USER -> permissions.stream().anyMatch(p ->
                    "USER".equals(p.getPermissionTargetType())
                            && Objects.equals(p.getPermissionTargetId(), user.getUserId()));
        };
    }
}
