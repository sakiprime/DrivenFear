package com.sakiprime.DrivenFear.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")      // 1. 对所有接口生效
                .allowedOriginPatterns("http://localhost:[*]") // 2. 允许这个前端地址访问
                .allowCredentials(true) // 3. ✅核心：允许携带Cookie/Session
                .allowedMethods("*")    // 4. 允许所有请求方式(GET/POST/PUT...)
                .allowedHeaders("*");   // 5. 允许所有请求头
    }
}
