package com.zjl.collaboration.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 客户端，调用网关用户接口获取用户信息。
 */
@Slf4j
@Component
public class GatewayUserClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.gateway.url:http://localhost:8086}")
    private String gatewayUrl;

    /**
     * 批量查询用户信息。
     *
     * @param userIds 用户 ID 列表
     * @return userId → UserInfo
     */
    public Map<Long, UserInfo> batchQuery(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl + "/api/system/users/batch?ids=" + ids))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
                Object data = body.get("data");
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    Map<Long, UserInfo> result = new LinkedHashMap<>();
                    dataMap.forEach((k, v) -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userMap = (Map<String, Object>) v;
                        Long userId = toLong(userMap.get("userId"));
                        result.put(userId, new UserInfo(
                                userId,
                                (String) userMap.get("username"),
                                (String) userMap.get("realName"),
                                toLong(userMap.get("deptId")),
                                (String) userMap.get("deptName")
                        ));
                    });
                    return result;
                }
            }
            log.warn("批量查询用户失败: status={}", response.statusCode());
        } catch (Exception e) {
            log.warn("批量查询用户异常: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 按 ID 查单个用户信息。
     */
    public UserInfo getById(Long userId) {
        if (userId == null) return null;
        Map<Long, UserInfo> map = batchQuery(List.of(userId));
        return map.get(userId);
    }

    /**
     * 按关键词搜索用户。
     */
    public List<UserInfo> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        try {
            String encoded = java.net.URLEncoder.encode(keyword.trim(), "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl + "/api/system/users/search?keyword=" + encoded + "&limit=" + limit))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
                Object data = body.get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) data;
                    return list.stream().map(m -> new UserInfo(
                            toLong(m.get("userId")),
                            (String) m.get("username"),
                            (String) m.get("realName"),
                            toLong(m.get("deptId")),
                            (String) m.get("deptName")
                    )).toList();
                }
            }
        } catch (Exception e) {
            log.warn("搜索用户异常: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) {}
        }
        return null;
    }
}
