package com.zjl.knowledge.service;

import com.zjl.knowledge.entity.KbDocument;

/**
 * 知识库 Milvus 路由服务
 *
 * <p>用于根据知识库文档的配置，决定向量化和向量写入相关策略，例如：</p>
 * <ul>
 *     <li>当前文档是否需要生成向量；</li>
 *     <li>向量写入哪个 Milvus Collection；</li>
 *     <li>向量化时使用哪个 embedding 模型 </li>
 * </ul>
 *
 * <p>该接口不直接执行向量化或 Milvus 写入操作，
 * 只负责提供路由决策，具体的 embedding 和写入逻辑由调用方完成。</p>
 *
 * @author zjl
 * @date 2026-05-22
 */
public interface KbMilvusRoutingService {

    /**
     * 判断当前文档是否需要进行向量化
     *
     * <p>通常用于在文档入库、更新或同步时判断是否需要生成 embedding
     * 如果文档所属知识库未启用向量检索，或者文档本身不适合向量化
     * 可以返回 {@code false} 跳过后续 embedding 流程。</p>
     *
     * @param doc 知识库文档
     * @return {@code true} 表示需要生成向量；{@code false} 表示跳过向量化
     */
    boolean shouldEmbed(KbDocument doc);

    /**
     * 获取当前文档写入向量时使用的 Milvus Collection
     *
     * <p>该方法一般用于严格路由场景：
     * 如果文档或知识库没有配置可用的 Collection，调用方可以选择抛出异常或中断流程。</p>
     *
     * @param doc 知识库文档
     * @return 当前文档对应的 Milvus Collection 名称
     */
    String collectionForVectorWrite(KbDocument doc);

    /**
     * 获取当前文档写入向量时使用的 Milvus Collection，如果未配置，则返回系统默认 Collection。
     *
     * <p>该方法适合需要兜底写入的场景，避免因为单个知识库未配置 Collection 而导致整个向量写入流程失败。</p>
     *
     * @param doc 知识库文档
     * @return 当前文档对应的 Collection 名称；未配置时返回默认 Collection
     */
    String collectionForVectorWriteOrDefault(KbDocument doc);

    /**
     * 获取当前文档向量化时使用的 embedding 模型。
     *
     * <p>如果文档或知识库配置了专属 embedding 模型，则优先返回该模型；
     * 如果没有配置，则返回系统默认模型。</p>
     *
     * <p>调用方可以根据返回结果决定调用指定模型的 embedding 接口，
     * 从而支持不同知识库使用不同的向量模型。</p>
     *
     * @param doc 知识库文档
     * @return embedding 模型名称；未配置时返回默认模型
     */
    String embeddingModelOrDefault(KbDocument doc);
}
