package com.zjl.knowledge.preprocess;

/**
 * 文档预处理器。
 */
public interface DocumentPreprocessor {

    /**
     * 判断当前预处理器是否支持该文档。
     *
     * @param context 预处理上下文
     * @return 是否支持
     */
    boolean supports(DocumentPreprocessingContext context);

    /**
     * 执行文档预处理。
     *
     * @param context 预处理上下文
     * @return 预处理结果
     */
    DocumentPreprocessingResult preprocess(DocumentPreprocessingContext context);

    /**
     * 是否为兜底预处理器。
     *
     * @return 是否兜底
     */
    default boolean fallback() {
        return false;
    }
}
