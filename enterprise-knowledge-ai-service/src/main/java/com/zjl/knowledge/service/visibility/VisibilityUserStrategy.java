package com.zjl.knowledge.service.visibility;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class VisibilityUserStrategy implements AbstractExecuteStrategy<VisibilityRequest, Boolean> {

    @Override
    public String mark() {
        return "USER";
    }

    @Override
    public Boolean executeResp(VisibilityRequest req) {
        return req.permissions().stream().anyMatch(p ->
                "USER".equals(p.getPermissionTargetType())
                        && Objects.equals(p.getPermissionTargetId(), req.user().getUserId()));
    }
}
