package com.zjl.web;

import com.zjl.common.api.ApiResponse;
import com.zjl.common.trace.TraceIdHolder;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统健康检查接口，提供服务可用性探测入口。
 */
@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    /**
     * 健康检查接口。
     *
     * @return 服务状态
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"), MDC.get(TraceIdHolder.key()));
    }
}
