package com.zjl.knowledge.dto.chunk;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 Chunk 请求。
 */
@Data
public class KbChunkCreateRequest {

    /**
     * 指定主键（可选，不传则雪花生成）
     */
    private Long chunkId;

    /**
     * 序号（可选，不传则追加在末尾）
     */
    private Integer index;

    @NotBlank
    private String content;
}
