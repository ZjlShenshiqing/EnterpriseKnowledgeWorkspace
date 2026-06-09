package com.zjl.framework.starter.log.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Log 注解打印，可以标记在类和方法上
 *
 * 标记在类上，类上的所有方法都会进行打印
 * 标记在方法上，仅打印标记方法
 * 类和方法上都有标记，以方法的标记为准
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ILog {

    /**
     * 入参打印
     *
     * @return 打印结果中是否包含入参，true就打印，否则不打印
     */
    boolean input() default true;

    /**
     * 出参打印
     *
     * @return 打印结果中是否包含出参，true就打印，否则不打印
     */
    boolean output() default true;
}