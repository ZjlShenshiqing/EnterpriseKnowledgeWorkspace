package com.zjl.knowledge.token;

/**
 * Token 计数（一期启发式，可替换为 tiktoken 等）。
 */
public interface TokenCounterService {

    int countTokens(String text);
}
