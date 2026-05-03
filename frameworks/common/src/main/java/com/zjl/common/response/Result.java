package com.zjl.common.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 统一接口响应体（code / message / data / traceId）。
 *
 * @param <T> 业务数据类型
 */
@Getter
@Setter
@Accessors(chain = true)
public class Result<T> {

    /**
     * 成功时的业务状态码
     */
    public static final String SUCCESS_CODE = "200";

    private String code;
    private String message;
    private T data;
    private String traceId;
}
