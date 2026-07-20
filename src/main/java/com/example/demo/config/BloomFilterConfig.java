package com.example.demo.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BloomFilterConfig {
    // RedisBloomFilter 相关配置
    @Bean
    public RBloomFilter<String> shortLinkBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("shortLinkBloomFilter");
        // 初始化布隆过滤器，预计插入 1000000 个元素，误判率为 0.01
        bloomFilter.tryInit(1000000L, 0.01);
        return bloomFilter;
    }
}
