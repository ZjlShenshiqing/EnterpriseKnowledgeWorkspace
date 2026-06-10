/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.designpattern.staregy;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.toolkit.ApplicationContextHolder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 策略选择器
 *
 * 使用了策略选择器 + 策略注册器 + 工厂模式
 *
 * @author zhangjlk
 * @date 2025/9/17 19:48
 */
public class AbstractStrategyChoose implements ApplicationListener<ContextRefreshedEvent> {

    /**
     * 执行策略集合，相当于策略工厂
     */
    private final Map<String, AbstractExecuteStrategy> abstractExecuteStrategyMap = new HashMap<>();

    /**
     * 根据mark执行具体策略
     *
     * 它支持两种模式：
     * 精确匹配：直接找名字完全一样的
     * 正则模糊匹配：通过 patternMatchMark 做通配符或规则匹配（需要开启 predicateFlag）
     *
     * @param mark           策略标识
     * @param predicateFlag  正则模糊匹配标识
     * @return
     */
    public AbstractExecuteStrategy choose(String mark, Boolean predicateFlag) {
        // 如果 predicateFlag == true → 走“模式匹配”逻辑
        if (predicateFlag != null && predicateFlag) {
            return abstractExecuteStrategyMap.values().stream()
                    .filter(each -> StringUtils.hasText(each.patternMatchMark()))
                    // 把当前策略的 patternMatchMark() 当作一个‘规则模板’（正则表达式），看看输入的 mark 字符串是否符合这个规则
                    .filter(each -> Pattern.compile(each.patternMatchMark()).matcher(mark).matches())
                    // 从一堆数据中，找到第一个符合条件的元素，并把它包装成 Optional 返回
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, "策略未定义"));
        }

        return Optional.ofNullable(abstractExecuteStrategyMap.get(mark))
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, String.format("[%s] 策略未定义", mark)));
    }

    /**
     * 根据mark查询具体策略并执行
     *
     * @param mark          策略标识
     * @param requestParam  执行策略入参
     * @param <REQUEST>     入参范型
     */
    public <REQUEST> void chooseAndExecute(String mark, REQUEST requestParam) {
        AbstractExecuteStrategy executeStrategy = choose(mark, null);
        executeStrategy.execute(requestParam);
    }

    /**
     * 根据mark查询具体策略并执行
     *
     * @param mark          策略标识
     * @param requestParam  执行策略入参
     * @param predicateFlag 模糊匹配标识
     * @param <REQUEST>     入参范型
     */
    public <REQUEST> void chooseAndExecute(String mark, REQUEST requestParam, Boolean predicateFlag) {
        AbstractExecuteStrategy executeStrategy = choose(mark, predicateFlag);
        executeStrategy.execute(requestParam);
    }

    /**
     * 根据mark查询具体策略并执行，带返回结果
     *
     * @param mark          策略标识
     * @param requestParam  执行策略入参
     * @param <REQUEST>     执行策略入参范型
     * @param <RESPONSE>    执行策略出参范型
     */
    public <REQUEST, RESPONSE> RESPONSE chooseAndExecuteResp(String mark, REQUEST requestParam) {
        AbstractExecuteStrategy executeStrategy = choose(mark, null);
        return (RESPONSE) executeStrategy.executeResp(requestParam);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 1. 从 Spring 容器中找出所有策略实现类
        Map<String, AbstractExecuteStrategy> actual = ApplicationContextHolder.getBeansOfType(AbstractExecuteStrategy.class);
        actual.forEach((beanName, bean) -> {
            // 2. 获取这个策略的 mark
            String mark = bean.mark();
            if (mark == null) {
                return; // 没有 mark 的策略不注册
            }
            AbstractExecuteStrategy beanExist = abstractExecuteStrategyMap.get(mark);
            if (beanExist != null) {
                // 3. 如果已经存在相同 mark 的策略 → 抛异常（不允许重复）
                throw new BizException(ErrorCode.SYSTEM_ERROR, String.format("[%s] Duplicate execution policy", mark));
            }
            // 4. 注册策略到策略工厂
            abstractExecuteStrategyMap.put(mark, bean);
        });
    }
}
