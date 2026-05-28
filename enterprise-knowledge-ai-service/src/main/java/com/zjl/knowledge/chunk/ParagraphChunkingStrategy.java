package com.zjl.knowledge.chunk;

import com.zjl.knowledge.domain.ChunkingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按段落（两个以上连续换行）切分，单段超长时降级为固定窗口切分
 */
@Component
@RequiredArgsConstructor
public class ParagraphChunkingStrategy implements ChunkingStrategy {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\R\\R+");

    private final FixedSizeChunkingStrategy fixedSizeChunkingStrategy;

    @Override
    public ChunkingMode mode() {
        return ChunkingMode.PARAGRAPH;
    }

    @Override
    public List<TextChunk> chunk(String text, ChunkingOptions options) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        int max = options.maxChars();
        List<TextChunk> out = new ArrayList<>();
        int idx = 0;

        Matcher matcher = PARAGRAPH_SPLIT.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            String block = text.substring(lastEnd, matcher.start()).trim();
            if (StringUtils.hasText(block)) {
                idx = appendBlock(block, lastEnd, max, options, out, idx);
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String block = text.substring(lastEnd).trim();
            if (StringUtils.hasText(block)) {
                idx = appendBlock(block, lastEnd, max, options, out, idx);
            }
        }

        if (out.isEmpty()) {
            for (TextChunk sub : fixedSizeChunkingStrategy.chunk(text, options)) {
                out.add(sub);
            }
        }
        return out;
    }

    private int appendBlock(String block, int offset, int max, ChunkingOptions options, List<TextChunk> out, int idx) {
        if (block.length() <= max) {
            out.add(new TextChunk(idx++, block, offset, offset + block.length()));
        } else {
            for (TextChunk sub : fixedSizeChunkingStrategy.chunk(block, options)) {
                out.add(new TextChunk(idx++, sub.content(), offset + sub.startOffset(), offset + sub.endOffset()));
            }
        }
        return idx;
    }
}
