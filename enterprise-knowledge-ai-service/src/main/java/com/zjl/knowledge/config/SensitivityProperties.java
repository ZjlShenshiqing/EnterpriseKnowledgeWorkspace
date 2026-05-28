package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 敏感词配置，绑定 {@code app.knowledge.sensitivity} 前缀
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.knowledge.sensitivity")
public class SensitivityProperties {

    /** 敏感关键词列表 */
    private List<String> keywords = List.of();

    /** 是否启用自动检测，默认 true */
    private boolean enabled = true;
}
