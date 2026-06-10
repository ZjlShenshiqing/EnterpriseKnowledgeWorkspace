package com.zjl.knowledge.service.visibility;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class VisibilityDepartmentStrategy implements AbstractExecuteStrategy<VisibilityRequest, Boolean> {

    @Override
    public String mark() {
        return "DEPARTMENT";
    }

    @Override
    public Boolean executeResp(VisibilityRequest req) {
        return req.doc().getDepartmentId() != null
                && Objects.equals(req.doc().getDepartmentId(), req.user().getDepartmentId());
    }
}
