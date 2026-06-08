package com.zjl.knowledge.tokenization;

import java.util.List;

/**
 * RAG 中文分词器。
 */
public interface ChineseTokenizer {

    /**
     * 使用查询模式分词。
     *
     * @param text 查询文本
     * @return 归一化 Token
     */
    List<String> tokenizeQuery(String text);

    /**
     * 使用文档模式分词。
     *
     * @param text 文档文本
     * @return 归一化 Token
     */
    List<String> tokenizeDocument(String text);
}
