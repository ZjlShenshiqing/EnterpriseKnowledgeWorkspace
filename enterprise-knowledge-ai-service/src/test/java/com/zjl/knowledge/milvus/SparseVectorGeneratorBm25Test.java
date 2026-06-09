package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.entity.KbTermStats;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbTermStatsMapper;
import com.zjl.knowledge.tokenization.ChineseTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparseVectorGeneratorBm25Test {

    @Mock
    private KbTermStatsMapper termStatsMapper;

    @Mock
    private KbDocumentChunkMapper chunkMapper;

    private SparseVectorGenerator generator;
    private RecordingTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        MilvusProperties props = new MilvusProperties();
        props.setSparseDimension(65535);
        tokenizer = new RecordingTokenizer();
        generator = new SparseVectorGenerator(props, tokenizer, termStatsMapper, chunkMapper);
    }

    // ── BM25 TF ──────────────────────────────────────────────

    @Test
    void bm25TfSaturatesAtHighFrequency() {
        float k1 = 1.2f;
        float max = (k1 + 1) * 1000 / (k1 + 1000);
        float tf1 = SparseVectorGenerator.bm25Tf(1);
        float tf1000 = SparseVectorGenerator.bm25Tf(1000);

        assertThat(tf1).isLessThan(tf1000);
        assertThat(tf1000).isCloseTo(max, offset(0.01f));
    }

    @Test
    void bm25TfForSingleOccurrence() {
        float k1 = 1.2f;
        float expected = (k1 + 1) / (k1 + 1); // = 1.0
        assertThat(SparseVectorGenerator.bm25Tf(1)).isEqualTo(expected);
    }

    @Test
    void bm25TfIncreasesSublinearly() {
        float tf2 = SparseVectorGenerator.bm25Tf(2);
        float tf4 = SparseVectorGenerator.bm25Tf(4);

        assertThat(tf2).isGreaterThan(1.0f);
        assertThat(tf4).isLessThan(tf2 * 2); // sublinear growth
    }

    // ── Smooth IDF ───────────────────────────────────────────

    @Test
    void smoothIdfCommonTermHasLowWeight() {
        double idfCommon = SparseVectorGenerator.smoothIdf(100, 90);
        double idfRare = SparseVectorGenerator.smoothIdf(100, 5);
        assertThat(idfCommon).isLessThan(idfRare);
    }

    @Test
    void smoothIdfRareTermHasHighWeight() {
        double idf = SparseVectorGenerator.smoothIdf(1000, 1);
        assertThat(idf).isGreaterThan(3.0);
    }

    @Test
    void smoothIdfZeroDfReturnsZero() {
        assertThat(SparseVectorGenerator.smoothIdf(100, 0)).isEqualTo(0.0);
    }

    @Test
    void smoothIdfWhenAllDocsContainTerm() {
        double idf = SparseVectorGenerator.smoothIdf(100, 100);
        assertThat(idf).isGreaterThan(0.0);
        assertThat(idf).isLessThan(0.7);
    }

    // ── toPosition ───────────────────────────────────────────

    @Test
    void toPositionDeterministic() {
        long pos1 = SparseVectorGenerator.toPosition("知识库", 65535);
        long pos2 = SparseVectorGenerator.toPosition("知识库", 65535);
        assertThat(pos1).isEqualTo(pos2);
    }

    @Test
    void toPositionInRange() {
        for (String term : List.of("a", "知识库", "OA-2025", "测试分词")) {
            long pos = SparseVectorGenerator.toPosition(term, 65535);
            assertThat(pos).isBetween(0L, 65534L);
        }
    }

    @Test
    void toPositionDifferentTermsDifferentPositions() {
        long pos1 = SparseVectorGenerator.toPosition("知识库", 65535);
        long pos2 = SparseVectorGenerator.toPosition("检索", 65535);
        long pos3 = SparseVectorGenerator.toPosition("文档", 65535);
        assertThat(pos1).isNotEqualTo(pos2);
        assertThat(pos2).isNotEqualTo(pos3);
    }

    // ── Document vector (BM25 TF, no IDF) ────────────────────

    @Test
    void documentVectorContainsBm25TfWeights() {
        tokenizer.documentTokens = List.of("知识", "库", "检索", "知识");

        Map<Long, Float> vec = generator.generateDocument("知识库检索知识");

        assertThat(vec).isNotEmpty();
        for (float w : vec.values()) {
            assertThat(w).isPositive();
        }
        /** "知识" appears twice, so its BM25 TF > 1.0 */
        long pos = SparseVectorGenerator.toPosition("知识", 65535);
        assertThat(vec.get(pos)).isGreaterThan(1.0f);
    }

    @Test
    void documentVectorWeightIndependentOfTotalTokens() {
        tokenizer.documentTokens = List.of("测试");
        Map<Long, Float> single = generator.generateDocument("测试");

        tokenizer.documentTokens = List.of("测试", "A", "B", "C", "D");
        Map<Long, Float> embedded = generator.generateDocument("测试ABCD");

        long pos = SparseVectorGenerator.toPosition("测试", 65535);
        assertThat(single.get(pos)).isEqualTo(embedded.get(pos));
    }

    // ── Query vector with IDF ─────────────────────────────────

    @Test
    void queryVectorUsesIdfFromMapper() {
        when(chunkMapper.selectCount(any())).thenReturn(100L);
        tokenizer.queryTokens = List.of("知识", "库");
        KbTermStats s1 = new KbTermStats();
        s1.setTerm("知识");
        s1.setDocCount(10);
        KbTermStats s2 = new KbTermStats();
        s2.setTerm("库");
        s2.setDocCount(50);
        when(termStatsMapper.selectByTerms(any())).thenReturn(List.of(s1, s2));

        Map<Long, Float> vec = generator.generateQuery("知识库");

        long posRare = SparseVectorGenerator.toPosition("知识", 65535);
        long posCommon = SparseVectorGenerator.toPosition("库", 65535);
        assertThat(vec.get(posRare)).isGreaterThan(vec.get(posCommon));
    }

    @Test
    void queryVectorFallbackToUniformIdfWhenMapperThrows() {
        tokenizer.queryTokens = List.of("知识", "库");
        when(termStatsMapper.selectByTerms(any())).thenThrow(new RuntimeException("DB down"));

        Map<Long, Float> vec = generator.generateQuery("知识库");

        assertThat(vec).hasSize(2);
        long posKnowledge = SparseVectorGenerator.toPosition("知识", 65535);
        long posLib = SparseVectorGenerator.toPosition("库", 65535);
        // uniform IDF means weight proportional to BM25 TF only
        assertThat(vec.get(posKnowledge)).isEqualTo(vec.get(posLib));
    }

    @Test
    void queryVectorReturnsEmptyForEmptyInput() {
        assertThat(generator.generateQuery("")).isEmpty();
        assertThat(generator.generateQuery(null)).isEmpty();
    }

    // ── IDF edge cases ───────────────────────────────────────

    @Test
    void queryVectorSkipsTermsWithZeroIdf() {
        when(chunkMapper.selectCount(any())).thenReturn(100L);
        tokenizer.queryTokens = List.of("罕见词");
        KbTermStats s = new KbTermStats();
        s.setTerm("罕见词");
        s.setDocCount(0);
        when(termStatsMapper.selectByTerms(any())).thenReturn(List.of(s));

        Map<Long, Float> vec = generator.generateQuery("罕见词");

        assertThat(vec).isEmpty();
    }

    @Test
    void queryVectorWhenTotalDocsIsZero() {
        when(chunkMapper.selectCount(any())).thenReturn(0L);
        tokenizer.queryTokens = List.of("知识");

        Map<Long, Float> vec = generator.generateQuery("知识");

        assertThat(vec).hasSize(1);
        assertThat(vec.values().iterator().next()).isEqualTo(1.0f);
    }

    // ── Rebuild ──────────────────────────────────────────────

    @Test
    void rebuildTermStatsSkipsWhenNoActiveChunks() {
        when(chunkMapper.selectCount(any())).thenReturn(0L);

        generator.rebuildTermStats();

        verify(termStatsMapper).truncate();
    }

    // ── Helpers ──────────────────────────────────────────────

    private static final class RecordingTokenizer implements ChineseTokenizer {

        List<String> queryTokens = List.of();
        List<String> documentTokens = List.of();

        @Override
        public List<String> tokenizeQuery(String text) {
            return new ArrayList<>(queryTokens);
        }

        @Override
        public List<String> tokenizeDocument(String text) {
            return new ArrayList<>(documentTokens);
        }
    }
}
