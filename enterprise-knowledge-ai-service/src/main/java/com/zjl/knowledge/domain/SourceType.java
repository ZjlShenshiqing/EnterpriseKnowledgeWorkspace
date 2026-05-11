package com.zjl.knowledge.domain;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import org.springframework.util.StringUtils;

/**
 * 文档来源类型
 */
public enum SourceType {

    FILE,
    URL;

    public static SourceType normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return FILE;
        }
        try {
            return SourceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "sourceType 非法: " + raw);
        }
    }
}
