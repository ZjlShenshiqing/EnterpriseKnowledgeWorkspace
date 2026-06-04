package com.zjl.knowledge.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 系统提示词测试。
 */
class AgentLoopPromptTest {

    @Test
    void systemPromptConstrainsMeetingOutputStyle() throws Exception {
        String prompt = buildSystemPrompt(false, false);

        assertThat(prompt)
                .contains("输出风格")
                .contains("不要使用 emoji")
                .contains("会议列表")
                .contains("不超过 5 条");
    }

    private static String buildSystemPrompt(boolean webSearchEnabled, boolean admin) throws Exception {
        Method method = AgentLoop.class.getDeclaredMethod("buildSystemPrompt", boolean.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(null, webSearchEnabled, admin);
    }
}
