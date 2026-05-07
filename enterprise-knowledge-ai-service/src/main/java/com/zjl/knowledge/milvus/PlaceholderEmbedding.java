package com.zjl.knowledge.milvus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 一期占位向量：由文本哈希展开为固定维度，保证可重复、可入库。
 * Step4 接入真实 embedding 后替换实现，不改变 Milvus 维度即可平滑切换。
 */
public final class PlaceholderEmbedding {

    private PlaceholderEmbedding() {
    }

    /**
     * 根据文本生成占位向量并做 L2 归一化。
     *
     * @param text 输入文本
     * @param dimension 向量维度
     * @return 向量
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
     * L2 归一化。
     *
     * @param v 向量
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
