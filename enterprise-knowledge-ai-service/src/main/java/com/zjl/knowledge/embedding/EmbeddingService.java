package com.zjl.knowledge.embedding;

import java.util.List;

/**
 * 文本向量化服务
 *
 * <p>用于将文本内容转换为 embedding 向量，
 * 供知识库检索、语义匹配、相似度计算等场景使用。</p>
 */
public interface EmbeddingService {

    /**
     * 使用默认 embedding 模型，对单条文本进行向量化。
     *
     * @param content 待向量化的文本内容
     * @return 文本对应的向量结果
     */
    List<Float> embed(String content);

    /**
     * 使用指定 embedding 模型，对单条文本进行向量化。
     *
     * <p>适用于不同知识库、不同业务场景需要使用不同向量模型的情况。</p>
     *
     * @param content 待向量化的文本内容
     * @param model   embedding 模型名称
     * @return 文本对应的向量结果
     */
    List<Float> embed(String content, String model);

    /**
     * 使用默认 embedding 模型，对多条文本进行批量向量化。
     *
     * <p>通常用于文档切片后批量生成向量，
     * 可以减少接口调用次数，提高处理效率。</p>
     *
     * @param texts 待向量化的文本列表
     * @return 每条文本对应的向量结果列表，顺序通常与输入 texts 保持一致
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 使用指定 embedding 模型，对多条文本进行批量向量化。
     *
     * <p>适用于按知识库或业务配置选择不同 embedding 模型的批量处理场景。</p>
     *
     * @param texts 待向量化的文本列表
     * @param model embedding 模型名称
     * @return 每条文本对应的向量结果列表，顺序通常与输入 texts 保持一致
     */
    List<List<Float>> embedBatch(List<String> texts, String model);
}
