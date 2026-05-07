package com.zjl.knowledge.dto.chunk;

import lombok.Data;

/**
 * Chunk 分页查询参数。
 */
@Data
public class KbChunkPageRequest {

    private long current = 1;
    private long size = 20;
    /**
     * 按启用状态过滤，null 表示不过滤
     */
    private Integer enabled;
}
