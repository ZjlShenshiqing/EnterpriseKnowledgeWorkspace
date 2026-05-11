package com.zjl.knowledge.token;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 按字符长度估算 token（与上传流程原逻辑一致）
 */
@Service
public class SimpleTokenCounterService implements TokenCounterService {

    @Override
    public int countTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
