package com.zjl.knowledge.embedding;

import java.util.List;

/**
 * 文本向量化（一期占位实现可对接真实 embedding 服务）。
 */
public interface EmbeddingService {

    List<Float> embed(String content);

    List<Float> embed(String content, String model);

    List<List<Float>> embedBatch(List<String> texts);

    List<List<Float>> embedBatch(List<String> texts, String model);
}
