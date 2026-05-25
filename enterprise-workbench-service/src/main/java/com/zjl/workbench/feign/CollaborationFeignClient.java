package com.zjl.workbench.feign;

import com.zjl.common.response.Result;
import com.zjl.workbench.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 协作服务（enterprise-collaboration-service）Feign 客户端
 */
@FeignClient(name = "enterprise-collaboration-service", configuration = FeignConfig.class)
public interface CollaborationFeignClient {

    @GetMapping("/api/todos")
    Result<List<Map<String, Object>>> getTodos(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);

    @GetMapping("/api/meetings/my")
    Result<List<Map<String, Object>>> getMyMeetings(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam(defaultValue = "") String userName);

    @GetMapping("/api/approvals")
    Result<List<Map<String, Object>>> getApprovals(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);

    @GetMapping("/api/tasks")
    Result<List<Map<String, Object>>> getTasks(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);

    @GetMapping("/api/intents")
    Result<Map<String, Object>> getIntents(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin,
            @RequestParam("current") int current,
            @RequestParam("size") int size);

    @GetMapping("/api/chat/unread-count")
    Result<Integer> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin);
}
