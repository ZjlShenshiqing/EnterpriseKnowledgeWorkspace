package com.zjl.collaboration.config;

import com.zjl.collaboration.web.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet Filter 注册配置
 *
 * <p>将 JWT 认证过滤器注册到 Servlet 容器，拦截所有 /api/* 请求</p>
 */
@Configuration
public class FilterConfig {

    /**
     * 注册 JWT 认证过滤器
     *
     * <p>该过滤器负责从请求头提取 token，解析用户信息并写入本地线程上下文，
     * 让下游业务代码可以直接获取当前登录用户</p>
     *
     * @param filter JWT 认证过滤器实例
     * @return FilterRegistrationBean，由 Servlet 容器管理
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        // 拦截所有 /api/* 路径
        reg.addUrlPatterns("/api/*");
        // 最高优先级，确保业务接口执行前完成认证
        reg.setOrder(1);
        return reg;
    }
}
