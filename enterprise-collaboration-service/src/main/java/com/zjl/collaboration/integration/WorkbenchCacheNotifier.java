package com.zjl.collaboration.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 业务数据变更后通知工作台失效 overview 缓存。
 */
@Slf4j
@Component
public class WorkbenchCacheNotifier {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${app.workbench.service-url:http://localhost:8084}")
    private String workbenchUrl;

    /**
     * 清除指定用户的工作台 overview 缓存。
     */
    public void evictOverview(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(workbenchUrl + "/api/workbench/cache/user/" + userId))
                    .DELETE()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                log.warn("刷新工作台缓存失败 userId={} status={}", userId, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("刷新工作台缓存失败 userId={}: {}", userId, e.getMessage());
        }
    }
}
