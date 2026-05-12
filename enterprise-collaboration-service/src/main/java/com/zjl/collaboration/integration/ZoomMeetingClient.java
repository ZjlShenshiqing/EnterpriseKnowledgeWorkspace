package com.zjl.collaboration.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class ZoomMeetingClient {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${zoom.account-id:}")
    private String accountId;

    @Value("${zoom.client-id:}")
    private String clientId;

    @Value("${zoom.client-secret:}")
    private String clientSecret;

    private String cachedToken;
    private long tokenExpiry;

    public boolean isConfigured() {
        return accountId != null && !accountId.isEmpty();
    }

    public Map<String, Object> createMeeting(String topic, String startTime, int durationMinutes) {
        if (!isConfigured()) return null;
        try {
            String token = getAccessToken();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("topic", topic);
            body.put("type", 2);
            body.put("start_time", startTime);
            body.put("duration", durationMinutes);
            body.put("timezone", "Asia/Shanghai");
            body.put("settings", Map.of("join_before_host", true, "host_video", false, "participant_video", false));

            HttpURLConnection conn = (HttpURLConnection) URI.create("https://api.zoom.us/v2/users/me/meetings").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(body));
            }
            if (conn.getResponseCode() == 201) {
                return mapper.readValue(conn.getInputStream(), new TypeReference<Map<String, Object>>() {});
            }
            log.warn("Zoom API error: {} {}", conn.getResponseCode(), new String(conn.getErrorStream().readAllBytes()));
        } catch (Exception e) { log.error("Zoom create meeting failed", e); }
        return null;
    }

    private String getAccessToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) return cachedToken;

        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=account_credentials&account_id=" + URLEncoder.encode(accountId, StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) URI.create("https://zoom.us/oauth/token").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic " + credentials);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes()); }
        Map<String, Object> resp = mapper.readValue(conn.getInputStream(), new TypeReference<Map<String, Object>>() {});
        cachedToken = resp.get("access_token").toString();
        tokenExpiry = System.currentTimeMillis() + ((Number) resp.get("expires_in")).longValue() * 1000 - 60000;
        return cachedToken;
    }
}
