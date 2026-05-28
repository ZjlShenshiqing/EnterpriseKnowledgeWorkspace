package com.zjl.knowledge.dto.chunk;

import lombok.Data;

import java.util.List;

/**
 * 批量更新 chunk 敏感级别请求
 */
@Data
public class ChunkSensitivityUpdateRequest {

    private List<ChunkSensitivityItem> updates;

    @Data
    public static class ChunkSensitivityItem {
        private Long chunkId;
        private String sensitivityLevel;
    }
}
