package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.tokenization.ChineseTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 稀疏向量查询与文档分词模式测试。
 */
class SparseVectorGeneratorModeTest {

    private RecordingChineseTokenizer chineseTokenizer;
    private SparseVectorGenerator generator;

    @BeforeEach
    void setUp() {
        MilvusProperties properties = new MilvusProperties();
        properties.setSparseDimension(65535);
        chineseTokenizer = new RecordingChineseTokenizer();
        generator = new SparseVectorGenerator(properties, chineseTokenizer);
    }

    @Test
    void queryVectorUsesSmartQueryTokens() {
        chineseTokenizer.queryTokens = List.of("差旅", "报销", "材料");

        Map<Long, Float> vector = generator.generateQuery("差旅报销材料");

        assertThat(vector).hasSize(3);
        assertThat(chineseTokenizer.queryInput).isEqualTo("差旅报销材料");
        assertThat(chineseTokenizer.documentInput).isNull();
    }

    @Test
    void documentVectorUsesMaximumWordDocumentTokens() {
        chineseTokenizer.documentTokens =
                List.of("中华人民共和国", "中华人民", "中华", "人民", "共和国");

        Map<Long, Float> vector = generator.generateDocument("中华人民共和国");

        assertThat(vector).isNotEmpty();
        assertThat(chineseTokenizer.documentInput).isEqualTo("中华人民共和国");
        assertThat(chineseTokenizer.queryInput).isNull();
    }

    @Test
    void identifierTokenContributesToSparseVector() {
        chineseTokenizer.queryTokens = List.of("oa-2025-001");

        Map<Long, Float> vector = generator.generateQuery("OA-2025-001");

        assertThat(vector).containsKey(SparseVectorGenerator.toPosition("oa-2025-001", 65535));
    }

    private static final class RecordingChineseTokenizer implements ChineseTokenizer {

        private List<String> queryTokens = List.of();
        private List<String> documentTokens = List.of();
        private String queryInput;
        private String documentInput;

        @Override
        public List<String> tokenizeQuery(String text) {
            queryInput = text;
            return queryTokens;
        }

        @Override
        public List<String> tokenizeDocument(String text) {
            documentInput = text;
            return documentTokens;
        }
    }
}
