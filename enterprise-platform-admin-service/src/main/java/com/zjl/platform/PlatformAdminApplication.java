package com.zjl.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@MapperScan("com.zjl.platform.mapper")
public class PlatformAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformAdminApplication.class, args);
    }
}
