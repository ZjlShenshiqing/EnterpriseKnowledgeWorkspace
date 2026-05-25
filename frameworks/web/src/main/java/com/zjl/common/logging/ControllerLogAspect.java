package com.zjl.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * Controller 请求日志切面 — 自动记录方法入口/出口/耗时/异常.
 * 通过 app.logging.controller.enabled=true（默认开启）控制开关.
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(value = "app.logging.controller.enabled", havingValue = "true", matchIfMissing = true)
public class ControllerLogAspect {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password", "passwd", "pwd",
        "token", "accessToken", "refreshToken",
        "secret", "secretKey", "authorization"
    );
    private static final int MAX_BODY_LENGTH = 500;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Controller *)")
    public void controllerPointcut() {}

    @Around("controllerPointcut() && execution(public * *(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = signature.getDeclaringType().getSimpleName();

        String httpMethod = "?";
        String uri = "/?";
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            httpMethod = request.getMethod();
            uri = request.getRequestURI();
        }

        String args = maskSensitive(toJson(joinPoint.getArgs()));
        if (args.length() > MAX_BODY_LENGTH) {
            args = args.substring(0, MAX_BODY_LENGTH) + "...";
        }

        log.info("→ {} {} | {}.{} | args={}", httpMethod, uri, className, methodName, args);

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("← {} {} 200 ({}ms)", httpMethod, uri, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("✗ {} {} 500 ({}ms): {}", httpMethod, uri, elapsed, e.getMessage(), e);
            throw e;
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String maskSensitive(String json) {
        String result = json;
        for (String key : SENSITIVE_KEYS) {
            result = result.replaceAll(
                "\"" + key + "\"\\s*:\\s*\"[^\"]*\"",
                "\"" + key + "\":\"***\"");
        }
        return result;
    }
}
