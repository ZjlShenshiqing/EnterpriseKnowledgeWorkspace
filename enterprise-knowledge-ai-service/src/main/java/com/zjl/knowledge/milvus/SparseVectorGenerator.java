package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 稀疏向量生成器
 *
 * <p>对文本做分词（中文按字符二元组，英文按空白拆分），统计词频后哈希到稀疏维度空间</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SparseVectorGenerator {

    private final MilvusProperties milvusProperties;

    /**
     * 对文本生成稀疏向量（词频归一化 + 哈希映射）
     *
     * @param text 输入文本，空/null 返回空 Map
     * @return 稀疏向量：位置 → 权重
     */
    public Map<Long, Float> generate(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Sparse vector input is empty, returning empty map");
            return Map.of();
        }
        List<String> tokens = tokenize(text);
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
     * 分词：中文走字符二元组 + 一元组，英文/数字按空白和标点拆分
     */
    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || isPunctuation(ch)) {
                addIfNotEmpty(tokens, buf);
                buf.setLength(0);
                continue;
            }
            if (isCjk(ch)) {
                addIfNotEmpty(tokens, buf);
                buf.setLength(0);
                tokens.add(String.valueOf(ch));
            } else {
                buf.append(ch);
            }
        }
        addIfNotEmpty(tokens, buf);

        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < text.length() - 1; i++) {
            char a = text.charAt(i);
            char b = text.charAt(i + 1);
            if (isCjk(a) && isCjk(b)) {
                bigrams.add(String.valueOf(a) + b);
            }
        }
        tokens.addAll(bigrams);
        return tokens;
    }

    private boolean isCjk(char ch) {
        return Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private boolean isPunctuation(char ch) {
        int type = Character.getType(ch);
        return type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private void addIfNotEmpty(List<String> tokens, StringBuilder buf) {
        if (buf.length() > 0) {
            tokens.add(buf.toString().toLowerCase());
        }
    }

    /**
     * 将 term 哈希到 [0, dim) 范围
     */
    static long toPosition(String term, int dim) {
        int h = term.hashCode();
        return ((long) h - Integer.MIN_VALUE) % dim;
    }
}
