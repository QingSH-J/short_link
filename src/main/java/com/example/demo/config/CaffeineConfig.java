package com.example.demo.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;


@Configuration
public class CaffeineConfig {
    @Bean
    public Cache<String, String> shortLinkCache(){
        return Caffeine.newBuilder()
                .maximumSize(10000) // 设置缓存的最大条目数
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }
}
