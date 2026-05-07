package com.zjl.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * enterprise-knowledge-ai-service 启动入口
 */
@SpringBootApplication(scanBasePackages = {"com.zjl.knowledge", "com.zjl.common"})
@ConfigurationPropertiesScan
@MapperScan("com.zjl.knowledge.mapper")
@EnableAsync
@EnableTransactionManagement
public class KnowledgeAiApplication {

    /**
     * 应用启动
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAiApplication.class, args);
    }
}
