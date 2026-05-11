package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库（逻辑 Milvus 集合 + 嵌入策略元数据）
 */
@Data
@TableName("kb_knowledge_base")
public class KbKnowledgeBase {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    /**
     * 嵌入模型标识；为空时使用全局 {@code app.knowledge-ai.embedding-model}
     */
    private String embeddingModel;

    /**
     * Milvus 集合名（与向量维度需与全局配置一致）
     */
    private String collectionName;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
