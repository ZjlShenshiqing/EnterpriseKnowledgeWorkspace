package com.zjl.knowledge.chunk;

import com.zjl.knowledge.domain.ChunkingMode;

import java.util.List;

/**
 * 文本分块策略接口
 *
 * <p>所有分块实现均通过 {@link ChunkingMode} 标识自身，
 * 由 {@link ChunkingStrategyFactory} 在启动时自动注册并路由。</p>
 */
public interface ChunkingStrategy {

    /**
     * 返回该策略对应的分块模式枚举
     *
     * @return 分块模式
     */
    ChunkingMode mode();

    /**
     * 将输入文本按策略切分为若干片段
     *
     * <p>空文本返回空列表；单段超长时策略内部可递归降级，
     * 例如按段落分块时单段超出上限则回退到固定窗口切分。</p>
     *
     * @param text    待切分的原始正文（不可为 null）
     * @param options 分块参数（最大字符数、重叠字符数等）
     * @return 分块结果列表，索引从 0 递增
     */
    List<TextChunk> chunk(String text, ChunkingOptions options);
}
