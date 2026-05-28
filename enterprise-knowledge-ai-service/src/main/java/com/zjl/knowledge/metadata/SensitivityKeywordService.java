package com.zjl.knowledge.metadata;

import com.zjl.knowledge.config.SensitivityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 敏感词检测：子串包含匹配，不区分大小写
 */
@Service
@RequiredArgsConstructor
public class SensitivityKeywordService {

    private final SensitivityProperties properties;

    /**
     * 检测文本是否命中敏感词
     *
     * @param text 待检测文本
     * @return 命中的关键词列表，未命中返回空列表
     */
    public List<String> match(String text) {
        if (!properties.isEnabled() || properties.getKeywords().isEmpty() || text == null) {
            return List.of();
        }
        String lower = text.toLowerCase();
        return properties.getKeywords().stream()
                .filter(kw -> lower.contains(kw.toLowerCase()))
                .toList();
    }

    /**
     * 判断是否命中任何敏感词
     */
    public boolean isSensitive(String text) {
        return !match(text).isEmpty();
    }
}
