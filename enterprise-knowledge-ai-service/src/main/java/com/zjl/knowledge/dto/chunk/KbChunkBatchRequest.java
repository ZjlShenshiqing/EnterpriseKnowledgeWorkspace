package com.zjl.knowledge.dto.chunk;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量操作 Chunk ID 列表
 */
@Data
public class KbChunkBatchRequest {

    @NotEmpty
    private List<Long> chunkIds;
}
