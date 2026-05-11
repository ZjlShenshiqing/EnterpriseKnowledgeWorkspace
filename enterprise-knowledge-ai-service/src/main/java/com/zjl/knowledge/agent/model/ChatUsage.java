package com.zjl.knowledge.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 用量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatUsage {

    /** 输入 token 数 */
    private int inputTokens;

    /** 输出 token 数 */
    private int outputTokens;
}
