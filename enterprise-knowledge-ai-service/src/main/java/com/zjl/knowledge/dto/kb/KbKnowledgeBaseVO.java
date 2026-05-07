package com.zjl.knowledge.dto.kb;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbKnowledgeBaseVO {

    private Long id;
    private String name;
    private String embeddingModel;
    private String collectionName;
    private Long ownerId;
    private long documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
