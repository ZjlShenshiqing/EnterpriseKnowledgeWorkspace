package com.zjl.knowledge.service;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.web.UserContext;

import java.util.List;

/**
 * 文档可见性服务
 */
public interface DocumentVisibilityService {

    boolean canView(KbDocument doc, UserContext user, List<KbDocumentPermission> permissions);
}
