package com.zjl.knowledge.dto.chunk;

import lombok.Data;

import java.util.List;

/**
 * 被自动标记为敏感的 chunk 视图
 */
@Data
public class ChunkSensitivityVO {

    private Long chunkId;
    private Integer chunkIndex;
    private List<String> matchedKeywords;
    private String sensitivityLevel;
    private String textPreview;
}
