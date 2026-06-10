package com.zjl.collaboration.service.intent;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

@Component
public class KeywordMatchStrategy implements AbstractExecuteStrategy<RuleMatchRequest, Boolean> {

    @Override
    public String mark() {
        return "keyword";
    }

    @Override
    public Boolean executeResp(RuleMatchRequest req) {
        return req.query().contains(req.expression());
    }
}
