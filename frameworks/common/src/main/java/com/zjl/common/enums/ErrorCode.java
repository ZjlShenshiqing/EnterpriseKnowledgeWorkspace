package com.zjl.common.enums;

import lombok.Getter;

/**
 * 错误码枚举（5 位数字分段）。
 *
 * <p>约定示例：</p>
 * <ul>
 *   <li>40xxx：客户端/参数类错误</li>
 *   <li>50xxx：服务端/系统类错误</li>
 *   <li>503xx：依赖服务不可用或写入失败（如向量库）</li>
 * </ul>
 *
 * <p>注意：接口成功码仍以项目约定的 200 为准；本枚举用于异常与失败响应。</p>
 */
@Getter
public enum ErrorCode {

    /**
     * 参数错误
     */
    PARAM_INVALID(40000, "请求参数不合法"),

    /**
     * 未授权
     */
    UNAUTHORIZED(40100, "未登录或登录已过期"),

    /**
     * 无权限
     */
    FORBIDDEN(40300, "无权限访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(40400, "资源不存在"),

    /**
     * 向量库写入失败
     */
    VECTOR_WRITE_FAILED(50301, "向量库写入失败，请检查 Milvus 服务与集合配置"),

    /**
     * 系统异常
     */
    SYSTEM_ERROR(50000, "系统异常");

    /**
     * 业务错误码
     */
    private final int code;

    /**
     * 面向前端的错误消息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
