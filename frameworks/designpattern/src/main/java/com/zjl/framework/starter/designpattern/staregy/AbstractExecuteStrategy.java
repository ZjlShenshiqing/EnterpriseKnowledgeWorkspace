/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.designpattern.staregy;

/**
 * 策略执行抽象类
 *
 * 用于实现“策略模式”，支持根据不同的业务标识（mark）动态选择并执行对应策略。
 * 所有具体策略需实现此接口，并覆盖必要的方法。
 *
 * @author zhangjlk
 * @date 2025/9/17 19:44
 */
public interface AbstractExecuteStrategy<REQUEST, RESPONSE> {

    /**
     * 获取策略的唯一标识（用于精确匹配）
     *
     * 例如："ALIPAY_PAY"、"WECHAT_PAY"、"NORMAL_ORDER"
     * 在策略工厂中通过该标识查找对应的策略实例。
     *
     * 默认返回 null，表示该策略不参与基于 mark 的精确匹配。
     * 子类应根据需要重写此方法。
     */
    default String mark() {
        return null;
    }

    /**
     * 获取策略的模式匹配标识（用于模糊/条件匹配）
     *
     * 适用于需要根据运行时动态条件匹配策略的场景。
     * 例如：不同地区、不同用户等级、不同商品类别触发不同策略。
     *
     * 默认返回 null，表示不支持模式匹配。
     * 可结合策略工厂进行正则、前缀、规则表达式等方式匹配。
     *
     * @return 模式匹配标识，如 "VIP_USER_*"、"REGION_CN_*"，否则返回 null
     */
    default String patternMatchMark() {
        return null;
    }

    /**
     * 执行策略
     *
     * @param requestParam 执行策略入参
     */
    default void execute(REQUEST requestParam) {

    }

    /**
     * 带返回值的执行策略
     *
     * @param requestParam 执行策略入参
     * @return 执行策略后的返回值
     */
    default RESPONSE executeResp(REQUEST requestParam) {
        return null;
    }
}
