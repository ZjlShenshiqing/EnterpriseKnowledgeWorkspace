package com.zjl.knowledge.embedding;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.milvus.PlaceholderEmbedding;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 占位向量：与 {@link PlaceholderEmbedding} 一致，后续可替换为 HTTP/SDK 调用真实模型
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.knowledge.embedding-model", havingValue = "", matchIfMissing = true)
public class PlaceholderEmbeddingService implements EmbeddingService {

    private final MilvusProperties milvusProperties;

    @Override
    public List<Float> embed(String content) {
        return toList(PlaceholderEmbedding.fromText(content, milvusProperties.getVectorDimension()));
    }

    @Override
    public List<Float> embed(String content, String model) {
        return embed(content);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embed(t));
        }
        return out;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String model) {
        return embedBatch(texts);
    }

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}
