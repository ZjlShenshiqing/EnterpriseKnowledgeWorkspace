package com.zjl.framework.starter.log.core;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.SystemClock;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import com.zjl.framework.starter.log.annotation.ILog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 日志打印AOP切面
 */
@Aspect
public class ILogPrintAspect {

    /**
     * 环绕通知：自动为标记了 @ILog 注解的方法打印结构化日志（含入参、出参、耗时、请求路径等）
     *
     * @param joinPoint 连接点（Join Point）：程序执行过程中可以被 AOP 拦截的特定点，一般都是方法
     * @return 原方法的返回值（透传）
     * @throws Throwable 原方法可能抛出的任何异常（必须声明 throws Throwable）
     */
    @Around("within(com.zjl.framework.starter.log.annotation.ILog) || @annotation(com.zjl.framework.starter.log.annotation.ILog)")
    public Object printMLog(ProceedingJoinPoint joinPoint) throws Throwable {

        // ① 记录方法开始执行的精确时间戳（用于计算耗时，单位：毫秒）
        long startTime = SystemClock.now();

        // ② 从连接点中提取方法签名信息（方法名、参数类型、返回类型、所属类等）
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();

        // ③ 获取与当前方法所在类对应的日志记录器（Logger 名 = 类全限定名），便于日志归类
        Logger log = LoggerFactory.getLogger(methodSignature.getDeclaringType());

        // ④ 获取人类可读的开始时间字符串（如 "2024-06-05 10:30:45"），用于日志展示
        String beginTime = DateUtil.now();

        // ⑤ 声明 result 变量，用于接收原方法的返回值
        Object result = null;
        try {
            result = joinPoint.proceed(); // 继续执行被拦截的原始方法
        } finally {
            Method targetMethod = joinPoint
                    .getTarget()
                    .getClass()
                    .getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());

            ILog logAnnotation = Optional
                    .ofNullable(targetMethod.getAnnotation(ILog.class))
                    .orElse(joinPoint.getTarget().getClass().getAnnotation(ILog.class));

            // 如果找到了 @ILog 注解，才打印详细日志
            if (logAnnotation != null) {
                // 创建日志数据传输对象（DTO），用于结构化存储日志内容
                ILogPrintDTO iLogPrintDTO = new ILogPrintDTO();
                iLogPrintDTO.setBeginTime(beginTime); // 设置开始时间（可读格式）

                // 根据注解配置决定是否记录输入参数
                if (logAnnotation.input()) {
                    iLogPrintDTO.setInputParams(buildInput(joinPoint));
                }

                // 根据注解配置决定是否记录输出结果
                if (logAnnotation.output()) {
                    iLogPrintDTO.setOutputParams(result); // 记录方法返回值
                }

                // === 尝试获取 HTTP 请求信息（仅适用于 Web 接口）===
                String methodType = "", requestURI = "";
                try {
                    // 从 Spring 的 RequestContextHolder 中获取当前请求上下文
                    ServletRequestAttributes servletRequestAttributes =
                            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (servletRequestAttributes != null) {
                        // 获取 HTTP 方法（GET/POST等）和请求路径
                        methodType = servletRequestAttributes.getRequest().getMethod();
                        requestURI = servletRequestAttributes.getRequest().getRequestURI();
                    }
                } catch (Exception ignored) {
                    // 忽略异常：非 Web 环境（如定时任务、MQ 消费者）没有 RequestContextHolder
                    // 此时 methodType 和 requestURI 保持为空字符串
                }

                // === 打印结构化日志 ===
                // 格式示例：
                // [POST] /api/user/create, executeTime: 45ms, info: {"beginTime":"2024-06-05 10:30:45","inputParams":["张三"],"outputParams":"success"}
                log.info("[{}] {}, executeTime: {}ms, info: {}",
                        methodType,                   // HTTP 方法（如 POST）
                        requestURI,                  // 请求路径（如 /api/user）
                        SystemClock.now() - startTime, // 方法执行耗时（毫秒）
                        JSON.toJSONString(iLogPrintDTO) // 将日志 DTO 转为 JSON 字符串
                );
            }
        }
        return result;
    }

    /**
     * 构建用于日志打印的“安全输入参数数组”。
     * <p>
     * 原始方法参数中可能包含不适合直接打印的对象（如 HTTP 请求/响应、二进制文件、字节数组等），
     * 直接序列化这些对象可能导致：
     *   - 日志爆炸（如打印整个 HttpServletRequest 内部结构）
     *   - 敏感信息泄露（如 Cookie、Header）
     *   - 内存溢出（如大文件字节数组）
     *   - 序列化异常（如 MultipartFile 不可序列化）
     * <p>
     * 本方法对这些“危险”或“无意义”的参数进行脱敏或替换，确保日志安全、简洁、可读。
     *
     * @param joinPoint AOP 连接点，用于获取被拦截方法的实际参数
     * @return 经过脱敏/过滤后的参数数组，可安全用于日志打印
     */
    private Object[] buildInput(ProceedingJoinPoint joinPoint) {
        // 1. 获取原始方法调用时传入的所有参数（Object 数组）
        Object[] args = joinPoint.getArgs();

        // 2. 创建一个新数组，用于存放“可打印”的参数（长度与原参数一致，保持位置对应）
        Object[] printArgs = new Object[args.length];

        // 3. 遍历每一个参数
        for (int i = 0; i < args.length; i++) {
            // 4. 【特殊处理 1】跳过 HttpServletRequest 和 HttpServletResponse
            //    原因：
            //      - 这两个对象结构庞大，包含大量内部状态、头信息、会话等
            //      - 直接 toString() 或 JSON 序列化会输出海量无用信息，甚至包含敏感数据（如 Authorization 头）
            //      - 在 Web 日志中，请求路径、方法等已有单独记录，无需重复打印整个 request 对象
            if (args[i] instanceof HttpServletRequest || args[i] instanceof HttpServletResponse) {
                continue; // 跳过后续处理
            }

            // 5. 【特殊处理 2】字节数组（byte[]）
            //    原因：
            //      - byte[] 通常代表二进制数据（如文件内容、图片、加密数据）
            //      - 直接打印会显示为内存地址（如 [B@1a2b3c4d）或乱码，毫无意义
            //      - 若内容很大，序列化时可能占用大量内存或日志空间
            if (args[i] instanceof byte[]) {
                printArgs[i] = "byte array"; // 替换为可读字符串，表明此处是字节数组
            }
            // 6. 【特殊处理 3】MultipartFile（Spring 上传文件对象）
            //    原因：
            //      - MultipartFile 包含文件流、临时路径、原始文件名等
            //      - 无法被 JSON 正常序列化，可能抛异常
            //      - 实际关心的是“是否上传了文件”，而非文件内容
            else if (args[i] instanceof MultipartFile) {
                printArgs[i] = "file"; // 替换为通用标识
            }
            // 7. 【默认情况】其他普通对象（如 String、Integer、DTO、POJO 等）
            //    这些对象通常可安全序列化，保留原始值用于日志打印
            else {
                printArgs[i] = args[i];
            }
        }

        // 8. 返回处理后的参数数组，供日志框架（如 JSON.toJSONString）安全使用
        return printArgs;
    }
}