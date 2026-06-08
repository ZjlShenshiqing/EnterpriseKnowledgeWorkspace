package com.zjl.knowledge.service.rerank;

import com.zjl.knowledge.config.RagRerankProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRerankServiceImplTest {

    @Mock
    private RagReranker reranker;

    private RagRerankProperties properties;
    private RagRerankServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new RagRerankProperties();
        lenient().when(reranker.supports(RerankStrategy.LOCAL_FEATURE)).thenReturn(true);
        service = new RagRerankServiceImpl(properties, List.of(reranker));
    }

    @Test
    void shouldPreserveOriginalOrderWhenDisabled() {
        properties.setEnabled(false);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "b", 0.9f, 2));
        candidates.add(candidate(1L, 11L, 1, "a", 0.5f, 1));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", candidates));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).originalRank()).isEqualTo(1);
        assertThat(result.get(1).originalRank()).isEqualTo(2);
        assertThat(result.get(0).rerankStrategy()).isEqualTo("");
    }

    @Test
    void shouldPreserveOriginalOrderWhenStrategyIsNone() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.NONE);

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "b", 0.9f, 2));
        candidates.add(candidate(1L, 11L, 1, "a", 0.5f, 1));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", candidates));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).originalRank()).isEqualTo(1);
        assertThat(result.get(1).originalRank()).isEqualTo(2);
    }

    @Test
    void shouldPreserveAllCandidateFieldsWhenDisabled() {
        properties.setEnabled(false);

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(new RerankedCandidate(
                1L, 10L, 3, "text", 0.8f, 1, "VECTOR_ONLY",
                Map.of("key", "value"), 0f, "", ""));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", candidates));

        RerankedCandidate c = result.get(0);
        assertThat(c.documentId()).isEqualTo(1L);
        assertThat(c.chunkId()).isEqualTo(10L);
        assertThat(c.chunkIndex()).isEqualTo(3);
        assertThat(c.text()).isEqualTo("text");
        assertThat(c.originalScore()).isEqualTo(0.8f);
        assertThat(c.metadata()).containsEntry("key", "value");
    }

    @Test
    void shouldFallbackToOriginalOrderOnException() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        properties.setFallbackToOriginalOrder(true);

        when(reranker.rerank(any())).thenThrow(new RuntimeException("simulated failure"));

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "b", 0.9f, 2));
        candidates.add(candidate(1L, 11L, 1, "a", 0.5f, 1));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", candidates));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).originalRank()).isEqualTo(1);
        assertThat(result.get(1).originalRank()).isEqualTo(2);
    }

    @Test
    void shouldThrowWhenFallbackDisabled() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        properties.setFallbackToOriginalOrder(false);

        when(reranker.rerank(any())).thenThrow(new RuntimeException("simulated failure"));

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "text", 0.8f, 1));

        assertThatThrownBy(() -> service.rerank(new RerankRequest("query", candidates)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fallback is disabled");
    }

    @Test
    void shouldPassRerankedResultsThrough() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);

        List<RerankedCandidate> input = new ArrayList<>();
        input.add(candidate(1L, 10L, 0, "a", 0.5f, 1));
        input.add(candidate(1L, 11L, 1, "b", 0.9f, 2));

        List<RerankedCandidate> rerankedOutput = List.of(
                new RerankedCandidate(1L, 11L, 1, "b", 0.9f, 2, "VECTOR_ONLY",
                        Map.of(), 0.95f, "LOCAL_FEATURE", "kw=1.00 title=0.00 src=0.50 len=0.80"),
                new RerankedCandidate(1L, 10L, 0, "a", 0.5f, 1, "VECTOR_ONLY",
                        Map.of(), 0.30f, "LOCAL_FEATURE", "kw=0.00 title=0.00 src=0.50 len=0.50")
        );
        when(reranker.rerank(any())).thenReturn(rerankedOutput);

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", input));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunkId()).isEqualTo(11L);
        assertThat(result.get(0).rerankScore()).isEqualTo(0.95f);
        assertThat(result.get(0).rerankStrategy()).isEqualTo("LOCAL_FEATURE");
    }

    @Test
    void shouldTruncateCandidatesAboveLimit() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        properties.setCandidateLimit(2);

        List<RerankedCandidate> input = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            input.add(candidate(1L, (long) (10 + i), i, "text" + i, 0.8f, i + 1));
        }

        List<RerankedCandidate> truncatedOutput = input.subList(0, 2);
        when(reranker.rerank(any())).thenReturn(truncatedOutput);

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", input));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFallbackWhenNoRerankerFound() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        RagRerankServiceImpl noMatch = new RagRerankServiceImpl(properties, List.of());

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "b", 0.9f, 2));
        candidates.add(candidate(1L, 11L, 1, "a", 0.5f, 1));

        List<RerankedCandidate> result = noMatch.rerank(new RerankRequest("query", candidates));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).originalRank()).isEqualTo(1);
    }

    private static RerankedCandidate candidate(Long docId, Long chunkId, int chunkIndex,
                                                String text, float score, int rank) {
        return new RerankedCandidate(
                docId, chunkId, chunkIndex, text, score, rank,
                "VECTOR_ONLY", Map.of(), 0f, "", ""
        );
    }
}
