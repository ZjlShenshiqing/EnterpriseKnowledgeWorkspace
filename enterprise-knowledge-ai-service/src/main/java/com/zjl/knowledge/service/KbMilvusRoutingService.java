package com.zjl.knowledge.service;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.KnowledgeAiProperties;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 根据文档关联的知识库解析 Milvus 集合名与嵌入模型（无 kb_id 时回落到全局配置）
 */
@Component
@RequiredArgsConstructor
public class KbMilvusRoutingService {

    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final KnowledgeAiProperties knowledgeAiProperties;

    /**
     * 是否应该执行向量化（全局开关关闭或无模型配置时返回 false）
     */
    public boolean shouldEmbed(KbDocument doc) {
        if (!knowledgeAiProperties.isVectorWriteEnabled()) {
            return false;
        }
        String model = embeddingModelOrDefault(doc);
        return StringUtils.hasText(model);
    }

    /**
     * 向量写入使用的 Milvus 集合名；{@code null} 表示使用 {@code app.milvus.collection}
     */
    public String collectionForVectorWrite(KbDocument doc) {
        if (doc == null || doc.getKbId() == null) {
            return null;
        }
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(doc.getKbId());
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属知识库不存在或已删除");
        }
        return kb.getCollectionName();
    }

    /**
     * 删除文档向量等场景：知识库不可用时回退默认集合，避免阻塞删除（极端情况下可能残留孤儿向量）
     */
    public String collectionForVectorWriteOrDefault(KbDocument doc) {
        try {
            return collectionForVectorWrite(doc);
        } catch (BizException ex) {
            return null;
        }
    }

    /**
     * 嵌入模型：知识库配置了则优先，否则使用全局默认
     */
    public String embeddingModelOrDefault(KbDocument doc) {
        if (doc == null || doc.getKbId() == null) {
            return knowledgeAiProperties.getEmbeddingModel();
        }
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(doc.getKbId());
        if (kb != null && StringUtils.hasText(kb.getEmbeddingModel())) {
            return kb.getEmbeddingModel();
        }
        return knowledgeAiProperties.getEmbeddingModel();
    }
}
