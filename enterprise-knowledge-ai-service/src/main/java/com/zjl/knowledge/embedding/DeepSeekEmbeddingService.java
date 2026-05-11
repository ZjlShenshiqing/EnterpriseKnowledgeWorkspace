package com.zjl.knowledge.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DeepSeek Embedding 服务实现（OpenAI 兼容格式）
 *
 * <p>当 app.knowledge.embedding-model 配置了值时启用</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "app.knowledge.embedding-model", matchIfMissing = false)
public class DeepSeekEmbeddingService implements EmbeddingService {

    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekEmbeddingService(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public List<Float> embed(String content) {
        return embedBatch(List.of(content)).get(0);
    }

    @Override
    public List<Float> embed(String content, String model) {
        return embedBatch(List.of(content), model).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return callEmbeddingApi(texts, null);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String model) {
        return callEmbeddingApi(texts, model);
    }

    private List<List<Float>> callEmbeddingApi(List<String> texts, String model) {
        AgentProperties.Llm config = agentProperties.getLlm();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model != null ? model : "deepseek-chat");
            body.put("input", texts);

            String json = objectMapper.writeValueAsString(body);
            String apiUrl = config.getBaseUrl().replaceAll("/$", "") + "/v1/embeddings";

            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("DeepSeek Embedding API error {}: {}", status, errorBody);
                throw new RuntimeException("Embedding API error: " + errorBody);
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return parseResponse(responseBody);

        } catch (Exception e) {
            log.error("DeepSeek Embedding 调用失败", e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<Float>> parseResponse(String responseBody) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

        return data.stream()
                .map(item -> {
                    List<Double> embedding = (List<Double>) item.get("embedding");
                    return embedding.stream()
                            .map(Double::floatValue)
                            .collect(Collectors.toList());
                })
                .collect(Collectors.toList());
    }
}
