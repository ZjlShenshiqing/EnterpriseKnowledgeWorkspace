package com.zjl.knowledge.service.impl;

import com.zjl.framework.starter.designpattern.staregy.AbstractStrategyChoose;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.visibility.VisibilityRequest;
import com.zjl.knowledge.web.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DocumentVisibilityServiceImpl implements DocumentVisibilityService {

    private final AbstractStrategyChoose strategyChoose;

    public DocumentVisibilityServiceImpl(AbstractStrategyChoose strategyChoose) {
        this.strategyChoose = strategyChoose;
    }

    @Override
    public boolean canView(KbDocument doc, UserContext user, List<KbDocumentPermission> permissions) {
        if (user.isAdmin()) {
            return true;
        }
        if (Objects.equals(doc.getOwnerId(), user.getUserId())) {
            return true;
        }
        return strategyChoose.chooseAndExecuteResp(
                doc.getPermissionType(),
                new VisibilityRequest(doc, user, permissions));
    }
}
