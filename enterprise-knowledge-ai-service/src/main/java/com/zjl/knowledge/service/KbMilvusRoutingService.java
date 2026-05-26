package com.zjl.knowledge.service;

import com.zjl.knowledge.entity.KbDocument;

/**
 * Milvus 路由服务
 */
public interface KbMilvusRoutingService {

    boolean shouldEmbed(KbDocument doc);

    String collectionForVectorWrite(KbDocument doc);

    String collectionForVectorWriteOrDefault(KbDocument doc);

    String embeddingModelOrDefault(KbDocument doc);
}
