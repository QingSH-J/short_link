package com.example.demo.config;
import java.time.Duration;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {
    
    @Bean
    public RRateLimiter createLimiter(RedissonClient redissonClient) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("shortLinkRateLimiter");
        // 初始化限流器，设置每秒最多允许 100 个请求，最大等待时间为 1 秒
        rateLimiter.trySetRate(RateType.OVERALL, 100, Duration.ofSeconds(1));
        return rateLimiter;
    }

    @Bean
    public RRateLimiter getLimiter(RedissonClient redissonClient) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("shortLinkGetLimiter");
        // 初始化限流器，设置每秒最多允许 2000 个请求，最大等待时间为 1 秒
        rateLimiter.trySetRate(RateType.OVERALL, 2000, Duration.ofSeconds(1));
        return rateLimiter;
    }
}
