package com.zjl.collaboration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication(scanBasePackages = {"com.zjl.collaboration", "com.zjl.common"})
@MapperScan({"com.zjl.collaboration.mapper", "com.zjl.collaboration.workflow.mapper"})
public class CollaborationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollaborationApplication.class, args);
    }
}
