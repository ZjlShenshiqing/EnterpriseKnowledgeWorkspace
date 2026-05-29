package com.zjl.knowledge.service.impl;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.KnowledgeAiProperties;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import com.zjl.knowledge.service.KbMilvusRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class KbMilvusRoutingServiceImpl implements KbMilvusRoutingService {

    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final KnowledgeAiProperties knowledgeAiProperties;

    @Override
    public boolean shouldEmbed(KbDocument doc) {
        if (!knowledgeAiProperties.isVectorWriteEnabled()) {
            return false;
        }
        String model = embeddingModelOrDefault(doc);
        return StringUtils.hasText(model);
    }

    @Override
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

    @Override
    public String collectionForVectorWriteOrDefault(KbDocument doc) {
        try {
            return collectionForVectorWrite(doc);
        } catch (BizException ex) {
            return null;
        }
    }

    @Override
    public String embeddingModelOrDefault(KbDocument doc) {
        /*
         * 如果文档为空，或者文档没有绑定知识库 ID，说明无法根据知识库配置选择 embedding 模型。
         *
         * 这种情况下直接使用系统配置的默认 embedding 模型作为兜底。
         */
        if (doc == null || doc.getKbId() == null) {
            return knowledgeAiProperties.getEmbeddingModel();
        }

        /*
         * 根据文档所属的知识库 ID，查询知识库配置
         *
         * embedding 模型是配置在知识库维度上的，
         * 所以这里需要先拿到知识库信息，再判断是否配置了专属模型。
         */
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(doc.getKbId());

        /*
         * 如果知识库存在，并且配置了有效的 embedding 模型，
         * 则优先使用知识库自己的模型。
         *
         * 这样可以支持不同知识库使用不同的向量模型，
         * 例如某些知识库使用通用模型，某些知识库使用更适合业务领域的模型。
         */
        if (kb != null && StringUtils.hasText(kb.getEmbeddingModel())) {
            return kb.getEmbeddingModel();
        }

        /*
         * 如果知识库不存在，或者知识库没有配置 embedding 模型，
         * 则回退到系统默认 embedding 模型。
         *
         * 这是兜底逻辑，保证即使没有单独配置，也能正常进行向量化。
         */
        return knowledgeAiProperties.getEmbeddingModel();
    }
}
