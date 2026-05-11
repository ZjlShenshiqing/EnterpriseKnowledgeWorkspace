package com.zjl.knowledge.service;

import com.zjl.knowledge.domain.DocumentPermissionType;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.web.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 判断当前用户是否可查看文档
 */
@Component
public class DocumentVisibilityService {

    /**
     * 是否可查看
     *
     * @param doc 文档
     * @param user 用户上下文
     * @param permissions 文档权限明细
     * @return 是否可见
     */
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
