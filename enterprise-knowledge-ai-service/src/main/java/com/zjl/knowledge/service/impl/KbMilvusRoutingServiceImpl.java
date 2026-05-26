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
