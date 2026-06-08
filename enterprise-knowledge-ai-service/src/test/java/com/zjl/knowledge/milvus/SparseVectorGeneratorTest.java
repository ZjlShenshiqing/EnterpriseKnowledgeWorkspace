package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.config.RagTokenizationProperties;
import com.zjl.knowledge.tokenization.DefaultIkTokenizationEngine;
import com.zjl.knowledge.tokenization.IkChineseTokenizer;
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
        generator = new SparseVectorGenerator(
                props,
                new IkChineseTokenizer(new DefaultIkTokenizationEngine(), new RagTokenizationProperties()));
    }

    @Test
    void emptyTextReturnsEmptyVector() {
        assertTrue(generator.generateQuery(null).isEmpty());
        assertTrue(generator.generateQuery("").isEmpty());
        assertTrue(generator.generateDocument("   ").isEmpty());
    }

    @Test
    void chineseTextIsTokenized() {
        Map<Long, Float> result = generator.generateDocument("知识库检索");
        assertFalse(result.isEmpty());
        assertTrue(result.values().stream().allMatch(w -> w > 0f));
    }

    @Test
    void shortQueryWorks() {
        Map<Long, Float> result = generator.generateQuery("OA");
        assertFalse(result.isEmpty());
    }

    @Test
    void mixedChineseAndEnglishWorks() {
        Map<Long, Float> result = generator.generateQuery("OA-2025-001 制度 3.2.1");
        assertFalse(result.isEmpty());
    }

    @Test
    void weightsAreNormalized() {
        Map<Long, Float> result = generator.generateDocument("测试");
        float sum = result.values().stream().reduce(0f, Float::sum);
        assertTrue(Math.abs(sum - 1.0f) < 0.15f, "weights should be roughly normalized");
    }
}
