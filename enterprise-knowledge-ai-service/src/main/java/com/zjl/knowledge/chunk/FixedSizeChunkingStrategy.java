package com.zjl.knowledge.chunk;

import com.zjl.knowledge.domain.ChunkingMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定字符窗口滑窗切分
 *
 * <p>从头向后滑动，窗口大小为 {@link ChunkingOptions#maxChars()}，
 * 相邻窗口之间按 {@link ChunkingOptions#overlapChars()} 重叠，
 * 确保上下文不丢失。</p>
 *
 * <p>例如 maxChars=500, overlapChars=50 时：
 * 第一块 [0, 500)，第二块 [450, 950)，依此类推直到文本末尾。</p>
 *
 * <p>空文本直接返回空列表。</p>
 */
@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    /**
     * 返回 {@link ChunkingMode#FIXED_SIZE} 模式标识。
     *
     * @return 固定大小分块模式
     */
    @Override
    public ChunkingMode mode() {
        return ChunkingMode.FIXED_SIZE;
    }

    /**
     * 执行固定窗口滑窗切分
     *
     * <p>每次截取 {@code maxChars} 长度的子串，去首尾空白后作为一块；
     * 下一块起点回退 {@code overlapChars} 以实现重叠
     * 当 overlap 超过 max-1 时自动修正为 max-1。</p>
     *
     * @param text    待切分文本
     * @param options 包含窗口大小和重叠量的参数
     * @return 索引递增的 chunk 列表
     */
    @Override
    public List<TextChunk> chunk(String text, ChunkingOptions options) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        int max = options.maxChars();
        int overlap = Math.min(options.overlapChars(), max - 1);
        List<TextChunk> out = new ArrayList<>();
        int idx = 0;
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(text.length(), pos + max);
            String slice = text.substring(pos, end).trim();
            if (StringUtils.hasText(slice)) {
                out.add(new TextChunk(idx++, slice));
            }
            if (end >= text.length()) {
                break;
            }
            pos = end - overlap;
            if (pos <= 0) {
                pos = end;
            }
        }
        return out;
    }
}
