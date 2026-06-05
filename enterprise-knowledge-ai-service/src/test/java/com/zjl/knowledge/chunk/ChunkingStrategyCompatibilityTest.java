package com.zjl.knowledge.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分块策略兼容性测试。
 */
class ChunkingStrategyCompatibilityTest {

    @Test
    void fixedSizeStrategyKeepsWindowAndOverlapBehavior() {
        FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy();

        List<TextChunk> chunks = strategy.chunk("abcdef", new ChunkingOptions(3, 1));

        assertThat(chunks)
                .extracting(TextChunk::content)
                .containsExactly("abc", "cde", "ef");
    }

    @Test
    void paragraphStrategySplitsOnBlankLinesAndFallsBackForLongParagraph() {
        ParagraphChunkingStrategy strategy = new ParagraphChunkingStrategy(new FixedSizeChunkingStrategy());

        List<TextChunk> chunks = strategy.chunk("第一段\n\n第二段", new ChunkingOptions(256, 0));

        assertThat(chunks)
                .extracting(TextChunk::content)
                .containsExactly("第一段", "第二段");
    }
}
