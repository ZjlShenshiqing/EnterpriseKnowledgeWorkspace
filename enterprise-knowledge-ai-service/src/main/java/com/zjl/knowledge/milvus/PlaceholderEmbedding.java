package com.zjl.knowledge.milvus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 一期占位向量生成工具
 *
 * <p>将文本 SHA-256 哈希展开为固定维度浮点数组并做 L2 归一化，
 * 保证同一文本始终生成相同向量，可用于入库和检索
 * Step4 接入真实 embedding 模型后替换实现即可平滑切换</p>
 */
public final class PlaceholderEmbedding {

    private PlaceholderEmbedding() {
    }

    /**
     * 根据文本生成占位向量并做 L2 归一化
     *
     * <p>算法：SHA-256(text) → 取每个字节值映射到 [-1, 1) 区间 →
     * 循环填充至目标维度 → L2 归一化</p>
     *
     * @param text      输入文本
     * @param dimension 目标向量维度
     * @return 归一化后的向量
     */
    public static float[] fromText(String text, int dimension) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            float[] v = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                int b = hash[i % hash.length] & 0xff;
                v[i] = (b / 127.5f) - 1.0f;
            }
            normalizeL2(v);
            return v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * L2 归一化：使向量模长为 1
     *
     * <p>零向量退化处理：置为 (1,0,0,...)</p>
     *
     * @param v 待归一化的向量，原地修改
     */
    private static void normalizeL2(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        float norm = (float) Math.sqrt(sum);
        if (norm < 1e-6f) {
            Arrays.fill(v, 0f);
            v[0] = 1f;
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
    }
}
