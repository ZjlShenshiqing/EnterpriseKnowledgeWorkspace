/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.common.toolkit;

import com.zjl.common.exception.BizException;
import com.zjl.common.enums.ErrorCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类
 *
 * 断言（Assertion）就是程序员写的‘自我检查’语句：用来确保程序运行到某一步时，某些关键条件必须满足
 *
 * @author zhangjlk
 * @date 2025/9/18 10:37
 */
public class Assert {

    /**
     * 举个例子：
     * Assert.isTrue(5 > 3, "5 应该大于 3 啊！");
     * 条件成立（true），什么都不发生，程序继续。
     *
     * Assert.isTrue(user.getAge() >= 18, "用户必须年满18岁");
     * 如果用户年龄是 16，条件为 false → 立刻抛出异常：
     * IllegalArgumentException: 用户必须年满18岁
     *
     * @param expression 条件
     * @param message    报错语句
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param expression 条件
     */
    public static void isTrue(boolean expression) {
        isTrue(expression, "[Assertion failed] - this expression must be true]");
    }

    /**
     * 断言传进来的参数为null
     *
     * @param object  传进来的对象
     * @param message 错误信息
     */
    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param object 传进来的对象
     */
    public static void isNull(Object object) {
        isNull(object, "[Assertion failed] - the object argument must be null");
    }

    /**
     * 断言传进来的参数不为null
     *
     * @param object  传进来的对象
     * @param message 错误信息
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param object 传进来的对象
     */
    public static void notNull(Object object) {
        notNull(object, "[Assertion failed] - the object argument must not be null");
    }

    /**
     * 断言对象不为null，否则抛出 BizException
     *
     * @param object    传进来的对象
     * @param errorCode 错误码
     */
    public static void notNull(Object object, ErrorCode errorCode) {
        if (object == null) {
            throw new BizException(errorCode);
        }
    }

    /**
     * 断定这个集合（比如 List、Set）不能是空的
     *
     * @param collection 要检查的集合（比如 List<String>、Set<User>）
     * @param message    如果为空显示什么错误信息
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param collection 要检查的集合（比如 List<String>、Set<User>）
     */
    public static void notEmpty(Collection<?> collection) {
        notEmpty(collection, "[Assertion failed] - the collection argument must not be empty: it must contain at least one element");
    }

    /**
     * 断定这个Map不能是空的
     *
     * @param map     传进来的map对象
     * @param message 显示什么错误信息
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param map 传进来的map对象
     */
    public static void notEmpty(Map<?, ?> map) {
        notEmpty(map, "[Assertion failed] - the map argument must not be empty； it must contain at least one entry");
    }

    /**
     * 断定这个字符串不能是空的
     *
     * @param str     字符串
     * @param message 错误消息
     */
    public static void notEmpty(String str, String message) {
        if (StringUtils.isEmpty(str)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param str 字符串
     */
    public static void notEmpty(String str) {
        notEmpty(str, "[Assertion failed] - the string argument must not be empty");
    }

    /**
     * 断言字符串不为空白
     *
     * @param str     字符串
     * @param message 错误消息
     */
    public static void notBlank(String str, String message) {
        if (org.apache.commons.lang3.StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param str 字符串
     */
    public static void notBlank(String str) {
        notBlank(str, "[Assertion failed] - the string argument must not be blank");
    }

    /**
     * 断定这个字符串必须有‘有意义的文字’，如果是 null 或全是空格，就立刻报错
     *
     * @param text    字符串
     * @param message 错误信息
     */
    public static void hasText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 默认消息的断言
     *
     * @param text 字符串
     */
    public static void hasText(String text) {
        hasText(text, "[Assertion failed] - the text argument must not be empty，null, or blank");
    }
}