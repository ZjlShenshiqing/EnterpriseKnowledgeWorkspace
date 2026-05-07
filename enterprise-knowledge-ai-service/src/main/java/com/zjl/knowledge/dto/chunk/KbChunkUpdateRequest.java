package com.zjl.knowledge.dto.chunk;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新 Chunk 请求。
 */
@Data
public class KbChunkUpdateRequest {

    @NotBlank
    private String content;
}
