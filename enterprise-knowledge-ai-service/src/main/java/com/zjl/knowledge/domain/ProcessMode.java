package com.zjl.knowledge.domain;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import org.springframework.util.StringUtils;

/**
 * 文档处理模式（CHUNK：策略分块；PIPELINE：编排引擎，当前工程可预留）
 */
public enum ProcessMode {

    CHUNK,
    PIPELINE;

    public static ProcessMode normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return CHUNK;
        }
        try {
            return ProcessMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "processMode 非法: " + raw);
        }
    }
}
