package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SparseVectorGeneratorTest {

    private SparseVectorGenerator generator;

    @BeforeEach
    void setUp() {
        MilvusProperties props = new MilvusProperties();
        props.setSparseDimension(65535);
        generator = new SparseVectorGenerator(props);
    }

    @Test
    void emptyTextReturnsEmptyVector() {
        assertTrue(generator.generate(null).isEmpty());
        assertTrue(generator.generate("").isEmpty());
        assertTrue(generator.generate("   ").isEmpty());
    }

    @Test
    void chineseTextIsTokenized() {
        Map<Long, Float> result = generator.generate("知识库检索");
        assertFalse(result.isEmpty());
        assertTrue(result.values().stream().allMatch(w -> w > 0f));
    }

    @Test
    void shortQueryWorks() {
        Map<Long, Float> result = generator.generate("OA");
        assertFalse(result.isEmpty());
    }

    @Test
    void mixedChineseAndEnglishWorks() {
        Map<Long, Float> result = generator.generate("OA-2025-001 制度 3.2.1");
        assertFalse(result.isEmpty());
    }

    @Test
    void weightsAreNormalized() {
        Map<Long, Float> result = generator.generate("测试");
        float sum = result.values().stream().reduce(0f, Float::sum);
        assertTrue(Math.abs(sum - 1.0f) < 0.15f, "weights should be roughly normalized");
    }
}
