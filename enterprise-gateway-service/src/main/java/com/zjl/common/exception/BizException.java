package com.zjl.common.exception;

import com.zjl.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常，携带业务错误码并由全局异常处理器统一转换为标准响应
 */
@Getter
public class BizException extends RuntimeException {

    /**
     * 业务错误码
     */
    private final int code;

    /**
     * 构造业务异常
     *
     * @param code 业务错误码
     * @param message 业务错误消息
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常（使用统一错误码枚举）
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 构造业务异常（使用统一错误码枚举，并允许覆盖提示语）
     *
     * @param errorCode 错误码枚举
     * @param message 覆盖错误消息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
