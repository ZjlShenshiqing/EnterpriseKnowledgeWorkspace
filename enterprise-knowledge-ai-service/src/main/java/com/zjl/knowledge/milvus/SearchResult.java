package com.zjl.knowledge.milvus;

import java.util.Map;

/**
 * Milvus 向量检索结果
 */
public record SearchResult(String chunkId, String docId, float score, Map<String, Object> metadata) {
}
