package com.zjl.knowledge.service.visibility;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class VisibilityProjectStrategy implements AbstractExecuteStrategy<VisibilityRequest, Boolean> {

    @Override
    public String mark() {
        return "PROJECT";
    }

    @Override
    public Boolean executeResp(VisibilityRequest req) {
        return req.user().getProjectId() != null && req.permissions().stream().anyMatch(p ->
                "PROJECT".equals(p.getPermissionTargetType())
                        && Objects.equals(p.getPermissionTargetId(), req.user().getProjectId()));
    }
}
