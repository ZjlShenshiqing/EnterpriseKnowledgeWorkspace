package com.zjl.common.exception;

/**
 * 可映射为统一失败响应的业务/领域异常基类。
 */
public abstract class AbstractException extends RuntimeException {

    protected AbstractException(String message) {
        super(message);
    }

    protected AbstractException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 对外暴露的错误码（字符串，与 {@link com.zjl.common.response.Result} 的 code 字段一致）。
     */
    public abstract String getErrorCode();
}
