package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.tokenization.ChineseTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 稀疏向量生成器
 *
 * <p>使用统一中文分词器生成词项，统计词频后哈希到稀疏维度空间</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SparseVectorGenerator {

    private final MilvusProperties milvusProperties;
    private final ChineseTokenizer chineseTokenizer;

    /**
     * 使用查询分词模式生成稀疏向量。
     *
     * @param text 输入文本，空/null 返回空 Map
     * @return 稀疏向量：位置 → 权重
     */
    public Map<Long, Float> generateQuery(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Sparse vector input is empty, returning empty map");
            return Map.of();
        }
        return generateFromTokens(chineseTokenizer.tokenizeQuery(text));
    }

    /**
     * 使用文档分词模式生成稀疏向量。
     *
     * @param text 文档文本，空/null 返回空 Map
     * @return 稀疏向量：位置 → 权重
     */
    public Map<Long, Float> generateDocument(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Sparse vector input is empty, returning empty map");
            return Map.of();
        }
        return generateFromTokens(chineseTokenizer.tokenizeDocument(text));
    }

    private Map<Long, Float> generateFromTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.merge(token, 1, Integer::sum);
        }
        int total = tokens.size();
        int dim = milvusProperties.getSparseDimension();
        Map<Long, Float> sparseVec = new HashMap<>();
        for (Map.Entry<String, Integer> entry : freq.entrySet()) {
            long pos = toPosition(entry.getKey(), dim);
            float weight = (float) entry.getValue() / total;
            sparseVec.merge(pos, weight, Float::sum);
        }
        return sparseVec;
    }

    /**
     * 将 term 哈希到 [0, dim) 范围
     */
    static long toPosition(String term, int dim) {
        int h = term.hashCode();
        return ((long) h - Integer.MIN_VALUE) % dim;
    }
}
