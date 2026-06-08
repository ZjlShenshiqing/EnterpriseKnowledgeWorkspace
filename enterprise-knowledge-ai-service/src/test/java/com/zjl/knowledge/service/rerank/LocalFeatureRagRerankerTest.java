package com.zjl.knowledge.service.rerank;

import com.zjl.knowledge.config.RagTokenizationProperties;
import com.zjl.knowledge.tokenization.DefaultIkTokenizationEngine;
import com.zjl.knowledge.tokenization.IkChineseTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFeatureRagRerankerTest {

    private LocalFeatureRagReranker reranker;

    @BeforeEach
    void setUp() {
        reranker = new LocalFeatureRagReranker(
                new IkChineseTokenizer(new DefaultIkTokenizationEngine(), new RagTokenizationProperties()));
    }

    @Test
    void shouldReorderByKeywordCoverage() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 10L, 0, "会议 时间 地点 安排", 0.7f, 1));
        candidates.add(candidate(1L, 11L, 1, "无关内容文本段落", 0.7f, 2));

        RerankRequest request = new RerankRequest("会议 时间 地点", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        assertThat(result).hasSize(2);
        // chunk 10 的关键词覆盖率更高
        assertThat(result.get(0).chunkId()).isEqualTo(10L);
        assertThat(result.get(0).rerankScore()).isGreaterThan(result.get(1).rerankScore());
        assertThat(result.get(0).rerankStrategy()).isEqualTo("LOCAL_FEATURE");
        assertThat(result.get(0).rerankReason()).isNotEmpty();
    }

    @Test
    void shouldBoostTitleOrSectionHit() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 20L, 0, "内容段落与差旅报销描述", 0.7f, 1,
                Map.of("title", "差旅报销指南")));
        candidates.add(candidate(1L, 21L, 1, "差旅报销相关内容段落", 0.7f, 2,
                Map.of()));

        RerankRequest request = new RerankRequest("差旅报销", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        assertThat(result).hasSize(2);
        // 两者 keyword coverage 相近时，标题命中"差旅报销"的候选应排更前
        assertThat(result.get(0).chunkId()).isEqualTo(20L);
        assertThat(result.get(0).rerankReason()).contains("title=");
    }

    @Test
    void shouldBoostSectionPathHit() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 30L, 0, "架构设计相关内容段落", 0.7f, 1,
                Map.of("sectionPath", "第三章 架构设计")));
        candidates.add(candidate(1L, 31L, 1, "架构设计相关描述段落", 0.7f, 2,
                Map.of()));

        RerankRequest request = new RerankRequest("架构设计", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        assertThat(result).hasSize(2);
        // 两者 keyword coverage 相近时，sectionPath 命中的候选应排更前
        assertThat(result.get(0).chunkId()).isEqualTo(30L);
        assertThat(result.get(0).rerankReason()).contains("title=");
    }

    @Test
    void shouldHandleEmptyQuery() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 40L, 0, "content a", 0.5f, 1));
        candidates.add(candidate(1L, 41L, 1, "content b", 0.9f, 2));

        RerankRequest request = new RerankRequest("", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        // 空 query 时按原始分数降序
        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunkId()).isEqualTo(41L);
    }

    @Test
    void shouldHandleBlankQuery() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 50L, 0, "content a", 0.5f, 1));
        candidates.add(candidate(1L, 51L, 1, "content b", 0.9f, 2));

        RerankRequest request = new RerankRequest("   ", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunkId()).isEqualTo(51L);
    }

    @Test
    void shouldComputeLengthQuality() {
        // 过短的 chunk
        float shortScore = reranker.computeLengthQuality("ab");
        // 接近最优的 chunk
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append("x");
        float optimalScore = reranker.computeLengthQuality(sb.toString());

        assertThat(optimalScore).isGreaterThan(shortScore);
    }

    @Test
    void shouldComputeKeywordCoverage() {
        float coverage = reranker.computeKeywordCoverage(
                "差旅报销需要填写申请表并提交给财务部门", List.of("差旅", "报销", "申请表"));
        assertThat(coverage).isEqualTo(1.0f);
    }

    @Test
    void shouldComputePartialKeywordCoverage() {
        float coverage = reranker.computeKeywordCoverage(
                "差旅报销需要填写申请表并提交给财务部门", List.of("差旅", "报销", "申请表", "审批"));
        assertThat(coverage).isGreaterThan(0.5f).isLessThan(1.0f);
    }

    @Test
    void shouldPreserveCandidateFieldsAfterRerank() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 60L, 3, "chunk text content", 0.75f, 5,
                Map.of("title", "Test")));

        RerankRequest request = new RerankRequest("test query", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        RerankedCandidate c = result.get(0);
        assertThat(c.documentId()).isEqualTo(1L);
        assertThat(c.chunkId()).isEqualTo(60L);
        assertThat(c.chunkIndex()).isEqualTo(3);
        assertThat(c.text()).isEqualTo("chunk text content");
        assertThat(c.originalScore()).isEqualTo(0.75f);
        assertThat(c.originalRank()).isEqualTo(5);
        assertThat(c.metadata()).containsEntry("title", "Test");
    }

    @Test
    void shouldIncludeDebugFields() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 70L, 0, "chunk about meeting time and location", 0.8f, 1));

        RerankRequest request = new RerankRequest("会议时间地点", candidates);
        List<RerankedCandidate> result = reranker.rerank(request);

        RerankedCandidate c = result.get(0);
        assertThat(c.rerankScore()).isGreaterThan(0f);
        assertThat(c.rerankStrategy()).isEqualTo("LOCAL_FEATURE");
        assertThat(c.rerankReason()).contains("kw=").contains("title=").contains("src=").contains("len=");
    }

    @Test
    void shouldRankChineseCandidateBySharedQueryTokens() {
        List<RerankedCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 80L, 0, "差旅审批流程和负责人说明", 0.7f, 1));
        candidates.add(candidate(1L, 81L, 1, "差旅报销需要提交发票和行程材料", 0.7f, 2));

        List<RerankedCandidate> result = reranker.rerank(
                new RerankRequest("差旅报销需要哪些材料", candidates));

        assertThat(result.get(0).chunkId()).isEqualTo(81L);
        assertThat(result.get(0).rerankReason()).contains("kw=");
    }

    private static RerankedCandidate candidate(Long docId, Long chunkId, int chunkIndex,
                                                String text, float score, int rank) {
        return candidate(docId, chunkId, chunkIndex, text, score, rank, Map.of());
    }

    private static RerankedCandidate candidate(Long docId, Long chunkId, int chunkIndex,
                                                String text, float score, int rank,
                                                Map<String, Object> metadata) {
        return new RerankedCandidate(
                docId, chunkId, chunkIndex, text, score, rank,
                "VECTOR_ONLY", metadata, 0f, "", ""
        );
    }
}
