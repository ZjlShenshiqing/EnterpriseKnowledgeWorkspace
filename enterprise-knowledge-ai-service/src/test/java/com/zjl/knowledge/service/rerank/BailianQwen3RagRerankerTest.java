package com.zjl.knowledge.service.rerank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zjl.knowledge.config.KnowledgeAiProperties;
import com.zjl.knowledge.config.RagRerankProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BailianQwen3RagRerankerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> requestBody = new AtomicReference<>();
    private HttpServer server;
    private RagRerankProperties rerankProperties;
    private KnowledgeAiProperties knowledgeProperties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/reranks", this::handleSuccess);
        server.start();

        rerankProperties = new RagRerankProperties();
        rerankProperties.setModel("qwen3-rerank");
        rerankProperties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        rerankProperties.setTopN(2);

        knowledgeProperties = new KnowledgeAiProperties();
        knowledgeProperties.setEmbeddingApiKey("test-bailian-key");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldCallBailianAndMapScoresToOriginalCandidates() {
        BailianQwen3RagReranker reranker = new BailianQwen3RagReranker(
                rerankProperties, knowledgeProperties, objectMapper);
        List<RerankedCandidate> candidates = List.of(
                candidate(10L, "差旅报销需要发票", 1),
                candidate(11L, "每周五举行技术分享", 2),
                candidate(12L, "住宿费按照职级报销", 3)
        );

        List<RerankedCandidate> result = reranker.executeResp(
                new RerankRequest("差旅报销需要哪些材料", candidates));

        assertThat(authorization.get()).isEqualTo("Bearer test-bailian-key");
        assertThat(requestBody.get())
                .containsEntry("model", "qwen3-rerank")
                .containsEntry("query", "差旅报销需要哪些材料")
                .containsEntry("top_n", 2);
        assertThat(requestBody.get().get("documents")).isEqualTo(List.of(
                "差旅报销需要发票",
                "每周五举行技术分享",
                "住宿费按照职级报销"
        ));
        assertThat(result).extracting(RerankedCandidate::chunkId)
                .containsExactly(12L, 10L);
    }

    @Test
    void shouldBeSelectedByRerankServiceForBailianStrategy() {
        rerankProperties.setEnabled(true);
        rerankProperties.setStrategy(RerankStrategy.BAILIAN_QWEN3);
        BailianQwen3RagReranker modelReranker = new BailianQwen3RagReranker(
                rerankProperties, knowledgeProperties, objectMapper);
        RagRerankService service = new RagRerankServiceImpl(rerankProperties, List.of(modelReranker));

        List<RerankedCandidate> result = service.executeResp(new RerankRequest(
                "差旅报销需要哪些材料",
                List.of(
                        candidate(10L, "差旅报销需要发票", 1),
                        candidate(11L, "每周五举行技术分享", 2),
                        candidate(12L, "住宿费按照职级报销", 3)
                )));

        assertThat(result).extracting(RerankedCandidate::chunkId)
                .containsExactly(12L, 10L);
        assertThat(result.get(0).rerankStrategy()).isEqualTo("BAILIAN_QWEN3");
    }

    @Test
    void shouldRejectCallWhenApiKeyIsMissing() {
        knowledgeProperties.setEmbeddingApiKey(" ");
        BailianQwen3RagReranker reranker = new BailianQwen3RagReranker(
                rerankProperties, knowledgeProperties, objectMapper);

        assertThatThrownBy(() -> reranker.executeResp(new RerankRequest(
                "差旅报销", List.of(candidate(10L, "需要发票", 1)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("百炼 API Key 未配置");
    }

    @Test
    void shouldIgnoreInvalidAndDuplicateIndexes() {
        replaceHandler("""
                {"results":[
                  {"index":1,"relevance_score":0.91},
                  {"index":1,"relevance_score":0.12},
                  {"index":99,"relevance_score":1.0},
                  {"index":-1,"relevance_score":1.0}
                ]}
                """, 200);
        BailianQwen3RagReranker reranker = new BailianQwen3RagReranker(
                rerankProperties, knowledgeProperties, objectMapper);
        List<RerankedCandidate> candidates = List.of(
                candidate(10L, "第一段", 1),
                candidate(11L, "第二段", 2),
                candidate(12L, "第三段", 3)
        );

        List<RerankedCandidate> result = reranker.executeResp(new RerankRequest("问题", candidates));

        assertThat(result).extracting(RerankedCandidate::chunkId)
                .containsExactly(11L);
        assertThat(result.get(0).rerankScore()).isEqualTo(0.91f);
    }

    @Test
    void shouldThrowControlledErrorForProviderFailureWithoutResponseContent() {
        replaceHandler("{\"message\":\"sensitive provider detail\"}", 500);
        BailianQwen3RagReranker reranker = new BailianQwen3RagReranker(
                rerankProperties, knowledgeProperties, objectMapper);

        assertThatThrownBy(() -> reranker.executeResp(new RerankRequest(
                "差旅报销", List.of(candidate(10L, "需要发票", 1)))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("百炼 rerank API 返回状态码 500")
                .hasMessageNotContaining("sensitive provider detail");
    }

    @Test
    void shouldRejectMalformedProviderResponse() {
        replaceHandler("{\"request_id\":\"test\"}", 200);
        BailianQwen3RagReranker reranker = new BailianQwen3RagReranker(
                rerankProperties, knowledgeProperties, objectMapper);

        assertThatThrownBy(() -> reranker.executeResp(new RerankRequest(
                "差旅报销", List.of(candidate(10L, "需要发票", 1)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("百炼 rerank 响应缺少 results");
    }

    private void handleSuccess(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(objectMapper.readValue(
                exchange.getRequestBody(), new TypeReference<Map<String, Object>>() {}));
        byte[] response = """
                {"results":[
                  {"index":2,"relevance_score":0.96},
                  {"index":0,"relevance_score":0.83}
                ]}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void replaceHandler(String responseBody, int status) {
        server.removeContext("/reranks");
        server.createContext("/reranks", exchange -> {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
    }

    private RerankedCandidate candidate(Long chunkId, String text, int rank) {
        return new RerankedCandidate(
                1L, chunkId, rank - 1, text, 1f / rank, rank,
                "VECTOR_ONLY", Map.of("title", "差旅制度"), 0f, "", ""
        );
    }
}
