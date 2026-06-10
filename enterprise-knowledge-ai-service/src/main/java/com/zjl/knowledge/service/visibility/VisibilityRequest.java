package com.zjl.knowledge.service.visibility;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.web.UserContext;

import java.util.List;

public record VisibilityRequest(KbDocument doc, UserContext user, List<KbDocumentPermission> permissions) {}
