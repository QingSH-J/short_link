package com.example.demo.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.repository.ShortLinkRepository;

/**
 * 定时把 Redis 里累计的点击增量刷回数据库。
 * 跳转时用 Redis INCR 计数（快），这里每分钟批量落库（减少 DB 写压力）。
 */
@Component
public class ClickCountSyncTask {
    private static final Logger log = LoggerFactory.getLogger(ClickCountSyncTask.class);
    private static final String CLICK_KEY_PREFIX = "click:";

    private final StringRedisTemplate redisTemplate;
    private final ShortLinkRepository shortLinkRepository;

    public ClickCountSyncTask(StringRedisTemplate redisTemplate, ShortLinkRepository shortLinkRepository) {
        this.redisTemplate = redisTemplate;
        this.shortLinkRepository = shortLinkRepository;
    }

    // 每 60 秒执行一次
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void syncClickCounts() {
        Set<String> keys;
        try {
            keys = redisTemplate.keys(CLICK_KEY_PREFIX + "*");
        } catch (Exception e) {
            log.warn("点击量同步：扫描 Redis key 失败: {}", e.getMessage());
            return;
        }
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int synced = 0;
        for (String key : keys) {
            try {
                // GETDEL：原子地取出增量并删除，避免与新的点击计数产生竞争
                String value = redisTemplate.opsForValue().getAndDelete(key);
                if (value == null) {
                    continue;
                }
                long delta = Long.parseLong(value);
                if (delta <= 0) {
                    continue;
                }
                String shortCode = key.substring(CLICK_KEY_PREFIX.length());
                int updated = shortLinkRepository.incrementClickCount(shortCode, delta);
                if (updated == 0) {
                    // 短链已被删除等：增量丢弃即可
                    log.debug("点击量同步：短码 {} 在库中不存在，丢弃增量 {}", shortCode, delta);
                } else {
                    synced++;
                }
            } catch (Exception e) {
                log.warn("点击量同步：处理 key={} 失败: {}", key, e.getMessage());
            }
        }
        if (synced > 0) {
            log.info("点击量同步完成，更新 {} 条短链", synced);
        }
    }
}
