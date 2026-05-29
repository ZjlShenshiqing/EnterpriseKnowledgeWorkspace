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
        // 优先使用调用方指定的模型；未指定时使用配置中的默认 embedding 模型。
        String effectiveModel = model != null ? model : properties.getEmbeddingModel();

        // 保存所有批次的 embedding 结果
        List<List<Float>> allResults = new ArrayList<>();

        // 按 MAX_BATCH_SIZE 分批调用，避免一次请求文本过多导致接口超限或请求过大。
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            // 计算当前批次的结束位置，最后一批不足 MAX_BATCH_SIZE 时取 texts.size()。
            int end = Math.min(i + MAX_BATCH_SIZE, texts.size());

            // 截取当前批次文本，subList 为左闭右开区间：[i, end)。
            List<String> batch = texts.subList(i, end);

            // 调用 embedding 接口，并将当前批次结果合并到总结果中。
            allResults.addAll(doCall(effectiveModel, batch));
        }

        return allResults;
    }

    private List<List<Float>> doCall(String model, List<String> texts) {
        // 拼接 Embedding API 地址，去掉 baseUrl 末尾的 /，避免出现重复斜杠。
        String apiUrl = properties.getEmbeddingBaseUrl().replaceAll("/$", "") + "/v1/embeddings";

        try {
            // 构造请求体，指定模型和需要向量化的文本列表。
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", texts);

            // 将请求体序列化为 JSON
            String json = objectMapper.writeValueAsString(body);

            // 创建 HTTP POST 连接
            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setRequestMethod("POST");

            // 设置鉴权信息和请求内容类型。
            conn.setRequestProperty("Authorization", "Bearer " + properties.getEmbeddingApiKey());
            conn.setRequestProperty("Content-Type", "application/json");

            // 开启请求体写入，并设置连接和读取超时时间。
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);

            // 写入 JSON 请求体。
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            // 判断接口响应状态，非 200 说明调用失败。
            int status = conn.getResponseCode();
            if (status != 200) {
                String errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("Bailian Embedding API error {}: {}", status, errorBody);
                throw new RuntimeException("Embedding API error: " + errorBody);
            }

            // 读取成功响应，并解析出向量结果。
            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Bailian Embedding 调用成功, model={}, texts={}", model, texts.size());
            return parseResponse(responseBody);

        } catch (Exception e) {
            // 统一记录调用失败日志，并向上抛出运行时异常。
            log.error("Bailian Embedding 调用失败", e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<Float>> parseResponse(String responseBody) throws Exception {
        // 将 Embedding API 返回的 JSON 字符串解析成 Map。
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        // 获取 data 字段，每个元素对应一条文本的向量结果。
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

        // 提取每条结果中的 embedding，并将 Jackson 默认解析出的 Double 转成 Float。
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
