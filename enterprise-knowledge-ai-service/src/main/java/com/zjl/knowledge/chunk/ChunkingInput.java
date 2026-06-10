package com.zjl.knowledge.chunk;

/**
 * 分块输入参数（适配设计模式策略接口）
 *
 * @author zhangjlk
 */
public record ChunkingInput(String text, ChunkingOptions options) {
    
    public static ChunkingInput of(String text, ChunkingOptions options) {
        return new ChunkingInput(text, options);
    }
}