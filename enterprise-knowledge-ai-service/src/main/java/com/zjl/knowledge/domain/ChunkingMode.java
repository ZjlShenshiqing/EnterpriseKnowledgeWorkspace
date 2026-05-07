package com.zjl.knowledge.domain;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import org.springframework.util.StringUtils;

/**
 * 分块策略类型
 */
public enum ChunkingMode {

    FIXED_SIZE,
    PARAGRAPH;

    public static ChunkingMode fromValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return FIXED_SIZE;
        }
        try {
            return ChunkingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "chunkStrategy 非法: " + raw);
        }
    }
}
