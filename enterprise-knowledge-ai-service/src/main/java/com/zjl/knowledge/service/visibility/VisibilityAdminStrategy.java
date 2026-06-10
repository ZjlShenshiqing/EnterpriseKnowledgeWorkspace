package com.zjl.knowledge.service.visibility;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

@Component
public class VisibilityAdminStrategy implements AbstractExecuteStrategy<VisibilityRequest, Boolean> {

    @Override
    public String mark() {
        return "ADMIN";
    }

    @Override
    public Boolean executeResp(VisibilityRequest req) {
        return false;
    }
}
