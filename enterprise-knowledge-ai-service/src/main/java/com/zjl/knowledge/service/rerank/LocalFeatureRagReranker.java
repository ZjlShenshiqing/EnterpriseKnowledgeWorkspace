package com.zjl.knowledge.service.rerank;

import com.zjl.knowledge.tokenization.ChineseTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 本地特征 reranker：基于关键词覆盖、标题命中、召回来源、chunk 长度等特征计算 rerank 分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFeatureRagReranker implements RagReranker {

    private static final int MIN_CHUNK_LENGTH = 50;
    private static final int OPTIMAL_CHUNK_LENGTH = 300;
    private static final int MAX_CHUNK_LENGTH = 2000;

    private static final float WEIGHT_RETRIEVAL_SCORE = 0.35f;
    private static final float WEIGHT_KEYWORD_COVERAGE = 0.30f;
    private static final float WEIGHT_TITLE_SECTION = 0.15f;
    private static final float WEIGHT_SOURCE = 0.10f;
    private static final float WEIGHT_LENGTH_QUALITY = 0.10f;

    private final ChineseTokenizer chineseTokenizer;

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.LOCAL_FEATURE;
    }

    @Override
    public List<RerankedCandidate> executeResp(RerankRequest request) {
        String query = request.query();
        if (query == null || query.isBlank()) {
            return request.candidates().stream()
                    .sorted(Comparator.comparingDouble(RerankedCandidate::originalScore).reversed())
                    .toList();
        }

        List<String> queryTokens = chineseTokenizer.tokenizeQuery(query);
        if (queryTokens.isEmpty()) {
            return request.candidates().stream()
                    .sorted(Comparator.comparingDouble(RerankedCandidate::originalScore).reversed())
                    .toList();
        }

        float maxOriginalScore = request.candidates().stream()
                .map(RerankedCandidate::originalScore)
                .max(Float::compareTo)
                .orElse(1f);

        List<RerankedCandidate> scored = new ArrayList<>();
        for (RerankedCandidate candidate : request.candidates()) {
            float retrievalScoreNorm = maxOriginalScore > 0
                    ? candidate.originalScore() / maxOriginalScore : 0f;

            float keywordCoverage = computeKeywordCoverage(candidate.text(), queryTokens);
            float titleSectionHit = computeTitleSectionHit(candidate.metadata(), queryTokens);
            float sourceWeight = computeSourceWeight(candidate.retrievalSource());
            float lengthQuality = computeLengthQuality(candidate.text());

            float rerankScore =
                    WEIGHT_RETRIEVAL_SCORE * retrievalScoreNorm
                    + WEIGHT_KEYWORD_COVERAGE * keywordCoverage
                    + WEIGHT_TITLE_SECTION * titleSectionHit
                    + WEIGHT_SOURCE * sourceWeight
                    + WEIGHT_LENGTH_QUALITY * lengthQuality;

            String reason = buildReason(keywordCoverage, titleSectionHit, sourceWeight, lengthQuality);

            scored.add(new RerankedCandidate(
                    candidate.documentId(),
                    candidate.chunkId(),
                    candidate.chunkIndex(),
                    candidate.text(),
                    candidate.originalScore(),
                    candidate.originalRank(),
                    candidate.retrievalSource(),
                    candidate.metadata(),
                    rerankScore,
                    RerankStrategy.LOCAL_FEATURE.name(),
                    reason
            ));
        }

        scored.sort(Comparator.comparingDouble(RerankedCandidate::rerankScore).reversed());
        return scored;
    }

    /**
     * 计算 query token 在 chunk 文本中的覆盖率（去重）
     */
    float computeKeywordCoverage(String chunkText, List<String> queryTokens) {
        if (chunkText == null || chunkText.isBlank() || queryTokens.isEmpty()) {
            return 0f;
        }
        String lowerText = chunkText.toLowerCase();
        long matched = queryTokens.stream()
                .distinct()
                .filter(lowerText::contains)
                .count();
        return (float) matched / queryTokens.stream().distinct().count();
    }

    /**
     * 计算 metadata 中标题和章节路径命中 query token 的分数
     */
    float computeTitleSectionHit(java.util.Map<String, Object> metadata, List<String> queryTokens) {
        if (metadata == null || metadata.isEmpty() || queryTokens.isEmpty()) {
            return 0f;
        }
        StringBuilder metaText = new StringBuilder();
        appendMetaField(metaText, metadata, "title");
        appendMetaField(metaText, metadata, "section");
        appendMetaField(metaText, metadata, "chapter");
        appendMetaField(metaText, metadata, "sectionPath");
        appendMetaField(metaText, metadata, "chapterPath");
        appendMetaField(metaText, metadata, "heading");

        if (metaText.isEmpty()) {
            return 0f;
        }
        String lowerMeta = metaText.toString().toLowerCase();
        long hits = queryTokens.stream()
                .distinct()
                .filter(lowerMeta::contains)
                .count();
        return (float) hits / queryTokens.stream().distinct().count();
    }

    private void appendMetaField(StringBuilder sb, java.util.Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value != null) {
            sb.append(' ').append(value.toString());
        }
    }

    /**
     * 检索来源加权：HYBRID 命中 > 单路命中
     */
    float computeSourceWeight(String retrievalSource) {
        if (retrievalSource == null) {
            return 0.5f;
        }
        return switch (retrievalSource.toUpperCase()) {
            case "HYBRID", "HYBRID_MILVUS" -> 1.0f;
            case "DENSE" -> 0.7f;
            case "SPARSE" -> 0.7f;
            default -> 0.5f;
        };
    }

    /**
     * chunk 长度质量评分：过短/过长惩罚
     */
    float computeLengthQuality(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return 0f;
        }
        int len = chunkText.length();
        if (len < MIN_CHUNK_LENGTH) {
            return (float) len / MIN_CHUNK_LENGTH;
        }
        if (len <= OPTIMAL_CHUNK_LENGTH) {
            float ratio = (float) (len - MIN_CHUNK_LENGTH) / (OPTIMAL_CHUNK_LENGTH - MIN_CHUNK_LENGTH);
            return 0.5f + 0.5f * ratio;
        }
        if (len <= MAX_CHUNK_LENGTH) {
            float ratio = (float) (len - OPTIMAL_CHUNK_LENGTH) / (MAX_CHUNK_LENGTH - OPTIMAL_CHUNK_LENGTH);
            return 1.0f - 0.5f * ratio;
        }
        return 0.5f;
    }

    private String buildReason(float keywordCoverage, float titleSectionHit, float sourceWeight, float lengthQuality) {
        List<String> parts = new ArrayList<>();
        parts.add(String.format("kw=%.2f", keywordCoverage));
        parts.add(String.format("title=%.2f", titleSectionHit));
        parts.add(String.format("src=%.2f", sourceWeight));
        parts.add(String.format("len=%.2f", lengthQuality));
        return String.join(" ", parts);
    }
}
