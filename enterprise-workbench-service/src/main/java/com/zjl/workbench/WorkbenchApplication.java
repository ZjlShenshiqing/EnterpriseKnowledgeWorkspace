package com.zjl.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableCaching
@EnableFeignClients
@SpringBootApplication(scanBasePackages = {"com.zjl.workbench", "com.zjl.common"})
public class WorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkbenchApplication.class, args);
    }
}
