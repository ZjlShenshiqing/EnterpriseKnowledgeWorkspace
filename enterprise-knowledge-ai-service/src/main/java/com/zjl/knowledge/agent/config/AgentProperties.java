package com.zjl.knowledge.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent + LLM 配置属性。
 */
@Data
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    /** LLM 配置 */
    private Llm llm = new Llm();

    /** 会话配置 */
    private Session session = new Session();

    @Data
    public static class Llm {

        /** LLM 提供商：deepseek / anthropic */
        private String provider = "deepseek";

        /** API Key */
        private String apiKey = "";

        /** 模型名 */
        private String model = "deepseek-chat";

        /** API 地址 */
        private String baseUrl = "https://api.deepseek.com";

        /** 最大输出 token */
        private int maxTokens = 4096;

        /** 温度参数（检索助手建议低温） */
        private double temperature = 0.3;
    }

    @Data
    public static class Session {

        /** 单次传给 LLM 的最大消息数 */
        private int maxHistory = 50;

        /** N 天未活动自动归档 */
        private int archiveAfterDays = 30;
    }
}
