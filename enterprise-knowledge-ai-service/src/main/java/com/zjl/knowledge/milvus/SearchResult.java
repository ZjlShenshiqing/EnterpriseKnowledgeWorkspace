package com.zjl.knowledge.milvus;

/**
 * Milvus 向量检索结果
 */
public record SearchResult(String chunkId, String docId, float score) {
}
