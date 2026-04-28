package com.zjl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * enterprise-gateway-service 启动入口。
 */
@SpringBootApplication
public class GatewaySpringbootStarter {

    /**
     * 应用启动方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewaySpringbootStarter.class, args);
    }
}