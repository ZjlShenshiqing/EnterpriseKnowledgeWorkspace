package com.zjl.knowledge.chunk;

import com.zjl.knowledge.domain.ChunkingMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定字符窗口滑窗切分
 */
@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    @Override
    public ChunkingMode mode() {
        return ChunkingMode.FIXED_SIZE;
    }

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
                out.add(new TextChunk(idx++, slice, pos, end));
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
