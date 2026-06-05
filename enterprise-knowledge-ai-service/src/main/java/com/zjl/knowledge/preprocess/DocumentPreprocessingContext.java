package com.zjl.knowledge.preprocess;

import com.zjl.knowledge.entity.KbDocument;

import java.util.Map;

/**
 * 文档预处理上下文。
 *
 * @param document       文档实体
 * @param parsedText     Tika 抽取正文
 * @param parsedMetadata Tika 抽取元数据
 */
public record DocumentPreprocessingContext(
        KbDocument document,
        String parsedText,
        Map<String, String> parsedMetadata
) {
}
