package com.zjl.knowledge.chunk;

/**
 * 分块后的文本片段，以不可变 record 承载
 *
 * <p>{@code index} 从 0 递增，用于标识切片在原文档中的顺序位置
 * {@code content} 为切分后的纯文本内容。</p>
 *
 * @param index   切片序号，从 0 开始递增
 * @param content 切片文本内容
 */
public record TextChunk(int index, String content) {
}
