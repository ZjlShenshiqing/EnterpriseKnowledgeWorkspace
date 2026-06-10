package com.zjl.knowledge.service.rerank;

import com.zjl.framework.starter.designpattern.staregy.AbstractStrategyChoose;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRerankServiceImplTest {

    @Mock
    private AbstractStrategyChoose strategyChoose;

    @Mock
    private RagReranker reranker;

    private RagRerankProperties properties;
    private RagRerankServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new RagRerankProperties();
        service = new RagRerankServiceImpl(properties, strategyChoose);
    }

    @Test
    void shouldPreserveOriginalOrderWhenDisabled() {
        properties.setEnabled(false);

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "b", 0.9f, 2));
        candidates.add(candidate(1L, 11L, 1, "a", 0.5f, 1));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", candidates));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).originalRank()).isEqualTo(1);
        assertThat(result.get(1).originalRank()).isEqualTo(2);
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
    }

    @Test
    void shouldFallbackToOriginalOrderOnException() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        properties.setFallbackToOriginalOrder(true);
        when(strategyChoose.choose(eq("LOCAL_FEATURE"), eq(false))).thenThrow(new RuntimeException("no strategy"));

        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "text", 0.8f, 1));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", candidates));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).originalRank()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenFallbackDisabled() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        properties.setFallbackToOriginalOrder(false);
        when(strategyChoose.choose(eq("LOCAL_FEATURE"), eq(false))).thenThrow(new RuntimeException("no strategy"));

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
        when(strategyChoose.choose(eq("LOCAL_FEATURE"), eq(false))).thenReturn(reranker);

        List<RerankedCandidate> input = new ArrayList<>();
        input.add(candidate(1L, 10L, 0, "a", 0.5f, 1));
        List<RerankedCandidate> output = List.of(
                new RerankedCandidate(1L, 10L, 0, "a", 0.5f, 1, "VECTOR_ONLY",
                        Map.of(), 0.95f, "LOCAL_FEATURE", "kw=1.00"));
        when(reranker.executeResp(any())).thenReturn(output);

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", input));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rerankScore()).isEqualTo(0.95f);
    }

    @Test
    void shouldTruncateCandidatesAboveLimit() {
        properties.setEnabled(true);
        properties.setStrategy(RerankStrategy.LOCAL_FEATURE);
        properties.setCandidateLimit(2);
        when(strategyChoose.choose(eq("LOCAL_FEATURE"), eq(false))).thenReturn(reranker);

        List<RerankedCandidate> input = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            input.add(candidate(1L, (long) (10 + i), i, "text" + i, 0.8f, i + 1));
        }
        when(reranker.executeResp(any())).thenReturn(input.subList(0, 2));

        List<RerankedCandidate> result = service.rerank(new RerankRequest("query", input));

        assertThat(result).hasSize(2);
    }

    private static RerankedCandidate candidate(Long docId, Long chunkId, int chunkIndex,
                                                String text, float score, int rank) {
        return new RerankedCandidate(
                docId, chunkId, chunkIndex, text, score, rank,
                "VECTOR_ONLY", Map.of(), 0f, "", "");
    }
}
