package com.zjl.collaboration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.im.oss")
public class ImOssProperties {
    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String bucket;
}
