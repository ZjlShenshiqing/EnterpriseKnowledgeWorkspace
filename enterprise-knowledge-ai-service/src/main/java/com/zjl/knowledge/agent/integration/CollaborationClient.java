package com.zjl.knowledge.agent.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.common.response.Result;
import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 enterprise-collaboration-service 的 HTTP 客户端（会议等协同能力）。
 */
@Slf4j
@Component
public class CollaborationClient {

    private static final TypeReference<Result<List<Map<String, Object>>>> LIST_RESULT =
            new TypeReference<>() {};
    private static final TypeReference<Result<Map<String, Object>>> MAP_RESULT =
            new TypeReference<>() {};
    private static final TypeReference<Result<Long>> LONG_RESULT =
            new TypeReference<>() {};
    private static final TypeReference<Result<Void>> VOID_RESULT =
            new TypeReference<>() {};

    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CollaborationClient(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 查询当前用户的会议列表。
     */
    public Result<List<Map<String, Object>>> listMyMeetings(UserContext user) throws Exception {
        String url = baseUrl() + "/api/meetings/my?userName=" + user.getUserId();
        return get(url, user, LIST_RESULT);
    }

    /**
     * 创建会议。
     */
    public Result<Long> createMeeting(UserContext user, Map<String, Object> body) throws Exception {
        return post(baseUrl() + "/api/meetings", user, body, LONG_RESULT);
    }

    /**
     * 检测会议室时间冲突。
     */
    public Result<Map<String, Object>> checkMeetingConflict(UserContext user, Map<String, Object> body)
            throws Exception {
        return post(baseUrl() + "/api/meetings/check-conflict", user, body, MAP_RESULT);
    }

    /**
     * 取消会议。
     */
    public Result<Void> cancelMeeting(UserContext user, Long meetingId) throws Exception {
        return delete(baseUrl() + "/api/meetings/" + meetingId, user, VOID_RESULT);
    }

    private String baseUrl() {
        String url = agentProperties.getCollaboration().getBaseUrl();
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        return url.trim().replaceAll("/+$", "");
    }

    private <T> Result<T> get(String url, UserContext user, TypeReference<Result<T>> type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        applyUserHeaders(builder, user);
        return parseResponse(builder.GET().build(), type);
    }

    private <T> Result<T> post(String url, UserContext user, Object body, TypeReference<Result<T>> type)
            throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        applyUserHeaders(builder, user);
        return parseResponse(builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(), type);
    }

    private <T> Result<T> delete(String url, UserContext user, TypeReference<Result<T>> type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        applyUserHeaders(builder, user);
        return parseResponse(builder.DELETE().build(), type);
    }

    private void applyUserHeaders(HttpRequest.Builder builder, UserContext user) {
        userHeaders(user).forEach(builder::header);
    }

    private Map<String, String> userHeaders(UserContext user) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-User-Id", String.valueOf(user.getUserId()));
        headers.put("X-Is-Admin", String.valueOf(user.isAdmin()));
        if (user.getDepartmentId() != null) {
            headers.put("X-Department-Id", String.valueOf(user.getDepartmentId()));
        }
        if (user.getProjectId() != null) {
            headers.put("X-Project-Id", String.valueOf(user.getProjectId()));
        }
        return headers;
    }

    private <T> Result<T> parseResponse(HttpRequest request, TypeReference<Result<T>> type) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.warn("协作服务调用失败: status={}, body={}", response.statusCode(), response.body());
            throw new IllegalStateException("协作服务返回 HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), type);
    }
}
