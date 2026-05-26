package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线定义 — 与知识库一一对应。
 */
@Data
@TableName("kb_pipeline")
public class KbPipeline {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long knowledgeBaseId;

    private String name;

    private String description;

    private String stages;

    private String chunkStrategy;

    private Integer vectorEnabled;

    private String embeddingModel;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
