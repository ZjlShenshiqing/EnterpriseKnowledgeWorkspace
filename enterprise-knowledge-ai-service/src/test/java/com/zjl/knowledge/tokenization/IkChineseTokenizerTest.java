package com.zjl.knowledge.tokenization;

import com.zjl.knowledge.config.RagTokenizationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IK 中文分词器测试。
 */
class IkChineseTokenizerTest {

    @Test
    void queryModeUsesSmartSegmentationForChineseQuestion() {
        ChineseTokenizer tokenizer = tokenizer(new DefaultIkTokenizationEngine(), true);

        List<String> tokens = tokenizer.tokenizeQuery("差旅报销需要哪些材料？");

        assertThat(tokens).contains("报销", "材料");
        assertThat(tokens).doesNotContain("差旅报销需要哪些材料");
        assertThat(tokens).hasSizeGreaterThan(2);
    }

    @Test
    void documentModeProducesMoreFineGrainedTermsThanQueryMode() {
        ChineseTokenizer tokenizer = tokenizer(new DefaultIkTokenizationEngine(), true);

        List<String> queryTokens = tokenizer.tokenizeQuery("中华人民共和国国歌");
        List<String> documentTokens = tokenizer.tokenizeDocument("中华人民共和国国歌");

        assertThat(documentTokens).containsAll(queryTokens);
        assertThat(documentTokens.size()).isGreaterThan(queryTokens.size());
    }

    @Test
    void preservesAndNormalizesMixedAsciiIdentifier() {
        ChineseTokenizer tokenizer = tokenizer(new DefaultIkTokenizationEngine(), true);

        List<String> tokens = tokenizer.tokenizeQuery("请查询 OA-2025-001 制度");

        assertThat(tokens).contains("oa-2025-001");
    }

    @Test
    void blankOrPunctuationInputReturnsEmptyTokens() {
        ChineseTokenizer tokenizer = tokenizer(new DefaultIkTokenizationEngine(), true);

        assertThat(tokenizer.tokenizeQuery(null)).isEmpty();
        assertThat(tokenizer.tokenizeQuery("   ")).isEmpty();
        assertThat(tokenizer.tokenizeDocument("，。！？")).isEmpty();
    }

    @Test
    void fallsBackToCharacterTermsWhenIkFails() {
        IkTokenizationEngine failingEngine = (text, smart) -> {
            throw new IllegalStateException("simulated IK failure");
        };
        ChineseTokenizer tokenizer = tokenizer(failingEngine, true);

        List<String> tokens = tokenizer.tokenizeQuery("差旅 OA-2025-001");

        assertThat(tokens).contains("差", "旅", "差旅", "oa-2025-001");
    }

    @Test
    void propagatesIkFailureWhenFallbackIsDisabled() {
        IkTokenizationEngine failingEngine = (text, smart) -> {
            throw new IllegalStateException("simulated IK failure");
        };
        ChineseTokenizer tokenizer = tokenizer(failingEngine, false);

        assertThatThrownBy(() -> tokenizer.tokenizeQuery("差旅报销"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated IK failure");
    }

    private ChineseTokenizer tokenizer(IkTokenizationEngine engine, boolean fallbackEnabled) {
        RagTokenizationProperties properties = new RagTokenizationProperties();
        properties.setEnabled(true);
        properties.setFallbackEnabled(fallbackEnabled);
        return new IkChineseTokenizer(engine, properties);
    }
}
