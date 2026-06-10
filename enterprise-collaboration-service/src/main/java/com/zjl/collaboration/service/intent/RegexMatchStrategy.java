package com.zjl.collaboration.service.intent;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class RegexMatchStrategy implements AbstractExecuteStrategy<RuleMatchRequest, Boolean> {

    @Override
    public String mark() {
        return "regex";
    }

    @Override
    public Boolean executeResp(RuleMatchRequest req) {
        try {
            return Pattern.compile(req.expression()).matcher(req.query()).find();
        } catch (Exception ignored) {
            return false;
        }
    }
}
