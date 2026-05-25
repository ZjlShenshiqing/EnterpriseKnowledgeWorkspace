package com.zjl.workbench.feign;

import com.zjl.common.response.Result;
import com.zjl.workbench.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 知识库服务（enterprise-knowledge-ai-service）Feign 客户端
 */
@FeignClient(name = "enterprise-knowledge-ai-service", configuration = FeignConfig.class)
public interface KnowledgeFeignClient {

    @GetMapping("/api/kb/documents")
    Result<Map<String, Object>> getDocuments(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);

    @GetMapping("/api/kb/bases")
    Result<Map<String, Object>> getBases(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);

    @GetMapping("/api/kb/agent/sessions")
    Result<Map<String, Object>> getAgentSessions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);
}
