package com.zjl.knowledge.service.rerank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.config.KnowledgeAiProperties;
import com.zjl.knowledge.config.RagRerankProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阿里云百炼 Qwen3 模型重排器。
 */
@Component
public class BailianQwen3RagReranker implements RagReranker {

    private final RagRerankProperties rerankProperties;
    private final KnowledgeAiProperties knowledgeProperties;
    private final ObjectMapper objectMapper;

    public BailianQwen3RagReranker(RagRerankProperties rerankProperties,
                                   KnowledgeAiProperties knowledgeProperties,
                                   ObjectMapper objectMapper) {
        this.rerankProperties = rerankProperties;
        this.knowledgeProperties = knowledgeProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.BAILIAN_QWEN3;
    }

    @Override
    public List<RerankedCandidate> executeResp(RerankRequest request) {
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) {
            return List.of();
        }
        if (!StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("Rerank query 不能为空");
        }
        if (!StringUtils.hasText(knowledgeProperties.getEmbeddingApiKey())) {
            throw new IllegalStateException("百炼 API Key 未配置");
        }

        try {
            String responseBody = callApi(request);
            return mapResponse(responseBody, request.candidates());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("百炼 rerank 调用失败: " + ex.getMessage(), ex);
        }
    }

    private String callApi(RerankRequest request) throws Exception {
        String apiUrl = rerankProperties.getBaseUrl().replaceAll("/$", "") + "/reranks";
        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        int timeout = Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(1L, rerankProperties.getTimeoutMs())));
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + knowledgeProperties.getEmbeddingApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", rerankProperties.getModel());
        body.put("query", request.query());
        body.put("documents", request.candidates().stream().map(RerankedCandidate::text).toList());
        body.put("top_n", Math.min(Math.max(1, rerankProperties.getTopN()), request.candidates().size()));

        byte[] requestBytes = objectMapper.writeValueAsBytes(body);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(requestBytes);
        }

        int status = connection.getResponseCode();
        try (InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream()) {
            String responseBody = readBody(input);
            if (status < 200 || status >= 300) {
                throw new RuntimeException("百炼 rerank API 返回状态码 " + status);
            }
            return responseBody;
        } finally {
            connection.disconnect();
        }
    }

    private List<RerankedCandidate> mapResponse(String responseBody,
                                                List<RerankedCandidate> candidates) throws Exception {
        Map<String, Object> response = objectMapper.readValue(
                responseBody, new TypeReference<Map<String, Object>>() {});
        Object resultsValue = response.get("results");
        if (!(resultsValue instanceof List<?> results)) {
            throw new IllegalStateException("百炼 rerank 响应缺少 results");
        }

        List<RerankedCandidate> reranked = new ArrayList<>();
        Set<Integer> includedIndexes = new HashSet<>();
        for (Object resultValue : results) {
            if (!(resultValue instanceof Map<?, ?> result)) {
                continue;
            }
            Integer index = numberAsInteger(result.get("index"));
            Float score = numberAsFloat(result.get("relevance_score"));
            if (index == null || score == null || index < 0 || index >= candidates.size()
                    || !includedIndexes.add(index)) {
                continue;
            }
            reranked.add(withRerankScore(candidates.get(index), score));
        }

        return reranked;
    }

    private RerankedCandidate withRerankScore(RerankedCandidate candidate, float score) {
        return new RerankedCandidate(
                candidate.documentId(),
                candidate.chunkId(),
                candidate.chunkIndex(),
                candidate.text(),
                candidate.originalScore(),
                candidate.originalRank(),
                candidate.retrievalSource(),
                candidate.metadata(),
                score,
                RerankStrategy.BAILIAN_QWEN3.name(),
                "model=" + rerankProperties.getModel()
        );
    }

    private Integer numberAsInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Float numberAsFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : null;
    }

    private String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
