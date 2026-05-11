package com.zjl.knowledge.chunk;

import com.zjl.knowledge.domain.ChunkingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 按段落（两个以上连续换行）切分，单段超长时降级为固定窗口切分
 *
 * <p>优先按 {@code \R\R+}（两个以上 Unicode 换行符）拆分，
 * 保持文档的自然段落结构。如果某段超过 {@link ChunkingOptions#maxChars()}，
 * 则委托 {@link FixedSizeChunkingStrategy} 对该段做滑窗切分。</p>
 *
 * <p>兜底策略：若文本无任何空行分隔（即整篇为单一段落），
 * 则直接退回固定窗口切分，保证所有文本都能被处理。</p>
 */
@Component
@RequiredArgsConstructor
public class ParagraphChunkingStrategy implements ChunkingStrategy {

    /**
     * 固定窗口策略，用于单段超长时的降级切分
     */
    private final FixedSizeChunkingStrategy fixedSizeChunkingStrategy;

    /**
     * 返回 {@link ChunkingMode#PARAGRAPH} 模式标识
     *
     * @return 按段落分块模式
     */
    @Override
    public ChunkingMode mode() {
        return ChunkingMode.PARAGRAPH;
    }

    /**
     * 按段落结构切分文本
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>按两个以上连续换行符拆分为段落块。</li>
     *   <li>每段去首尾空白，长度在 {@code maxChars} 内直接作为一个 chunk。</li>
     *   <li>超长段落委托 {@link FixedSizeChunkingStrategy} 做滑窗切分。</li>
     *   <li>若整个文本无任何段落边界，直接退回固定窗口切分。</li>
     * </ol>
     *
     * @param text    待切分文本
     * @param options 分块参数
     * @return 保持段落语义的 chunk 列表
     */
    @Override
    public List<TextChunk> chunk(String text, ChunkingOptions options) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        int max = options.maxChars();
        List<TextChunk> out = new ArrayList<>();
        int idx = 0;
        for (String block : text.split("\\R\\R+")) {
            String p = block.trim();
            if (!StringUtils.hasText(p)) {
                continue;
            }
            if (p.length() <= max) {
                out.add(new TextChunk(idx++, p));
            } else {
                for (TextChunk sub : fixedSizeChunkingStrategy.chunk(p, options)) {
                    out.add(new TextChunk(idx++, sub.content()));
                }
            }
        }
        if (out.isEmpty()) {
            return fixedSizeChunkingStrategy.chunk(text, options);
        }
        return out;
    }
}
