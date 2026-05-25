package com.zjl.knowledge.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.config.KnowledgeAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * 阿里云百炼 Embedding 服务（DashScope 兼容模式，OpenAI 格式）
 */
@Slf4j
@Service
public class BailianEmbeddingService implements EmbeddingService {

    private final KnowledgeAiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BailianEmbeddingService(KnowledgeAiProperties properties) {
        this.properties = properties;
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

    private static final int MAX_BATCH_SIZE = 10;

    private List<List<Float>> callEmbeddingApi(List<String> texts, String model) {
        String effectiveModel = model != null ? model : properties.getEmbeddingModel();

        List<List<Float>> allResults = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);
            allResults.addAll(doCall(effectiveModel, batch));
        }
        return allResults;
    }

    private List<List<Float>> doCall(String model, List<String> texts) {
        String apiUrl = properties.getEmbeddingBaseUrl().replaceAll("/$", "") + "/v1/embeddings";

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", texts);

            String json = objectMapper.writeValueAsString(body);

            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + properties.getEmbeddingApiKey());
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
                log.error("Bailian Embedding API error {}: {}", status, errorBody);
                throw new RuntimeException("Embedding API error: " + errorBody);
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Bailian Embedding 调用成功, model={}, texts={}", model, texts.size());
            return parseResponse(responseBody);

        } catch (Exception e) {
            log.error("Bailian Embedding 调用失败", e);
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
