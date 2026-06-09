package com.zjl.common.logging;

import com.zjl.framework.starter.log.annotation.ILog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Controller 请求日志切面 — 自动记录方法入口/出口/耗时/异常.
 * 通过 app.logging.controller.enabled=true（默认开启）控制开关.
 * 
 * 注意：如果方法或类上已经标记了 @ILog 注解，则由 ILogPrintAspect 处理，
 * 本切面会跳过这些方法以避免重复日志。
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(value = "app.logging.controller.enabled", havingValue = "true", matchIfMissing = true)
public class ControllerLogAspect {

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Controller *)")
    public void controllerPointcut() {}

    /**
     * 排除已标记 @ILog 的方法和类
     */
    @Pointcut("@annotation(com.zjl.framework.starter.log.annotation.ILog) || within(@com.zjl.framework.starter.log.annotation.ILog *)")
    public void iLogAnnotated() {}

    @Around("controllerPointcut() && execution(public * *(..)) && !iLogAnnotated()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();

        log.info("→ {}.{} | entering", className, methodName);

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("← {}.{} | completed ({}ms)", className, methodName, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("✗ {}.{} | failed ({}ms): {}", className, methodName, elapsed, e.getMessage(), e);
            throw e;
        }
    }
}
