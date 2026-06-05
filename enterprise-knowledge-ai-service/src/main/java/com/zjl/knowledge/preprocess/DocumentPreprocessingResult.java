package com.zjl.knowledge.preprocess;

import java.util.Map;

/**
 * 文档预处理结果。
 *
 * @param chunkInputText        进入分块策略的标准化文本
 * @param documentMetadata     写入文档 metadata 的扩展字段
 * @param chunkMetadataDefaults 写入每个 chunk metadata 的默认扩展字段
 */
public record DocumentPreprocessingResult(
        String chunkInputText,
        Map<String, Object> documentMetadata,
        Map<String, Object> chunkMetadataDefaults
) {
}
