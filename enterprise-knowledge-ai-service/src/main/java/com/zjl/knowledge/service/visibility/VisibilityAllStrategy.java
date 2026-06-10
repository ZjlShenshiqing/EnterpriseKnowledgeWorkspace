package com.zjl.knowledge.service.visibility;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

@Component
public class VisibilityAllStrategy implements AbstractExecuteStrategy<VisibilityRequest, Boolean> {

    @Override
    public String mark() {
        return "ALL";
    }

    @Override
    public Boolean executeResp(VisibilityRequest req) {
        return true;
    }
}
