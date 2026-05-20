package com.zjl.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码相关配置
 * <p>向 Spring 容器注册 BCryptPasswordEncoder Bean，供登录和用户模块统一注入使用</p>
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt 密码编码器 Bean
     * <p>BCrypt 是一种自适应单向哈希算法，内部自动生成随机盐值，
     * 相同明文每次加密结果不同，可有效防止彩虹表攻击</p>
     *
     * @return BCryptPasswordEncoder 实例，默认 strength=10（2^10 轮哈希）
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        // 默认 10 轮哈希迭代，兼顾安全性与性能
        return new BCryptPasswordEncoder();
    }
}

