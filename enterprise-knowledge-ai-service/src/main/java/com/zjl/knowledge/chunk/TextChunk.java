package com.zjl.knowledge.chunk;

/**
 * 分块后的文本片段
 *
 * @param index       切片序号，从 0 开始递增
 * @param content     切片文本内容
 * @param startOffset 在原文中的起始字符位置（含）
 * @param endOffset   在原文中的结束字符位置（不含）
 */
public record TextChunk(int index, String content, int startOffset, int endOffset) {
}
