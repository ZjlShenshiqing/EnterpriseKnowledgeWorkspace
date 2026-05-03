package com.zjl.common.enums;

import lombok.Getter;

/**
 * 通用底层错误码（映射到 {@link ErrorCode}，对外以字符串形式出现在响应体中）。
 */
@Getter
public enum BaseErrorCode {

    /**
     * 未分类服务端错误
     */
    SERVICE_ERROR(ErrorCode.SYSTEM_ERROR);

    private final ErrorCode delegate;

    BaseErrorCode(ErrorCode delegate) {
        this.delegate = delegate;
    }

    /**
     * 字符串错误码（与历史 int 码一致，便于前端兼容）。
     */
    public String code() {
        return String.valueOf(delegate.getCode());
    }

    /**
     * 默认错误文案
     */
    public String message() {
        return delegate.getMessage();
    }
}
