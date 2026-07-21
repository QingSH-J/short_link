package com.example.demo.service;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.redisson.api.RBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.demo.dto.CreateShortLinkResponse;
import com.example.demo.dto.GetShortLinkResponse;
import com.example.demo.dto.ShortLinkDetailResponse;
import com.example.demo.entity.ShortLink;
import com.example.demo.repository.ShortLinkRepository;
import com.example.demo.util.HashidsCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.example.demo.util.ValidateUrl;
@Service
public class ShortLinkService {
    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    private final GetIdService getIdService;
    private final ShortLinkRepository shortLinkRepository;
    private final StringRedisTemplate redisTemplate;
    private final HashidsCode hashidsCode;
    private final RBloomFilter<String> shortLinkBloomFilter;
    private final Cache<String, String> shortLinkCache;

    public ShortLinkService(GetIdService getIdService, ShortLinkRepository shortLinkRepository, StringRedisTemplate redisTemplate, HashidsCode hashidsCode, RBloomFilter<String> shortLinkBloomFilter, Cache<String, String> shortLinkCache) {
        this.getIdService = getIdService;
        this.shortLinkRepository = shortLinkRepository;
        this.redisTemplate = redisTemplate;
        this.hashidsCode = hashidsCode;
        this.shortLinkBloomFilter = shortLinkBloomFilter;
        this.shortLinkCache = shortLinkCache;
    }

    @Transactional
    public CreateShortLinkResponse createShortLink(String originalURL, String bigTag){
        
        URI validatedURI = ValidateUrl.validate(originalURL);
        String validatedURL = validatedURI.toString();
        long id = getIdService.getId(bigTag);
        String shortCode = hashidsCode.encode(id);

        ShortLink shortlink = new ShortLink();
        shortlink.setOriginalUrl(validatedURL);
        shortlink.setShortCode(shortCode);
        shortlink.setStatus("active");
        shortlink.setCreatedAt(OffsetDateTime.now());

        shortlink.setExpirationDate(OffsetDateTime.now().plusDays(30)); // 设置过期时间为30天后
        try {
            shortLinkRepository.save(shortlink);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Short code already exists: " + shortCode, e);
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit(){
                    // 事务提交后：把新短码加入布隆过滤器（提交后再 add，避免回滚留下永久假阳性）
                    shortLinkBloomFilter.add(shortCode);
                    // 将短链接存入 Redis，设置过期时间为30天
                    try {
                        redisTemplate.opsForValue().set("shortlink:" + shortCode, validatedURL, Duration.ofDays(30));
                    } catch (Exception e){
                        log.warn("Redis 预热缓存失败 shortCode={}: {}", shortCode, e.getMessage());
                    }
                }
            }
        );

        shortLinkBloomFilter.add(shortCode);
        
        return new CreateShortLinkResponse(shortCode, validatedURL);
    }

    public GetShortLinkResponse getShortLink(String shortCode){
        //布隆过滤器判断是否存在
        if (!shortLinkBloomFilter.contains(shortCode)){
            throw new IllegalArgumentException("Short code not found: " + shortCode);
        }

        // 一级缓存：本地 Caffeine（纳秒级，命中则连 Redis 都不碰）
        String local = shortLinkCache.getIfPresent(shortCode);
        if (local != null){
            incrementClickCount(shortCode);
            return new GetShortLinkResponse(shortCode, local);
        }

        //redis降级：二级缓存
        String originalURL = null;
        try {
            originalURL = redisTemplate.opsForValue().get("shortlink:" + shortCode);
        } catch (Exception e){
            // Redis 不可用：降级到数据库查询
            log.warn("Redis 读取失败，降级到数据库查询 shortCode={}: {}", shortCode, e.getMessage());
        }
        if (originalURL != null){
            // 空串是"不存在"的防穿透标记，命中它要当作不存在处理，不能返回空 URL
            if (originalURL.isEmpty()){
                throw new IllegalArgumentException("Short code not found: " + shortCode);
            }
            shortLinkCache.put(shortCode, originalURL);   // 回填一级缓存
            incrementClickCount(shortCode);
            return new GetShortLinkResponse(shortCode, originalURL);
        }

        ShortLink shortlink = shortLinkRepository.findByShortCode(shortCode);
        if (shortlink == null){
            // 防穿透：缓存"不存在"标记，用短 TTL（60秒），避免该短码之后被创建时长期被空值挡住
            // redis降级
            try {
                redisTemplate.opsForValue().set("shortlink:" + shortCode, "", Duration.ofSeconds(60));
            } catch (Exception e){
                // Redis 不可用：无法缓存空值标记，忽略即可（下次仍会走 DB）
                log.warn("Redis 写入空值标记失败 shortCode={}: {}", shortCode, e.getMessage());
            }
            throw new IllegalArgumentException("Short code not found: " + shortCode);
        }
        if (shortlink.getExpirationDate() != null && shortlink.getExpirationDate().isBefore(OffsetDateTime.now())){
            throw new IllegalArgumentException("Short code has expired: " + shortCode);
        }

        if (!"active".equals(shortlink.getStatus())){
            throw new IllegalArgumentException("Short code is not active: " + shortCode);
        }
        shortLinkCache.put(shortCode, shortlink.getOriginalUrl());   // 回填一级缓存
        //redis降级
        try {
            redisTemplate.opsForValue().set("shortlink:" + shortCode, shortlink.getOriginalUrl(), Duration.ofDays(30));
        } catch (Exception e){
            // Redis 不可用：回填缓存失败，不影响本次返回
            log.warn("Redis 回填缓存失败 shortCode={}: {}", shortCode, e.getMessage());
        }

        incrementClickCount(shortCode);

        return new GetShortLinkResponse(shortCode, shortlink.getOriginalUrl());
    }

    /**
     * 点击量 +1：用 Redis INCR 计数（原子、极快），后台定时任务再批量刷回数据库。
     * Redis 挂掉不影响跳转，计数丢失可接受。
     */
    private void incrementClickCount(String shortCode){
        try {
            redisTemplate.opsForValue().increment("click:" + shortCode);
        } catch (Exception e){
            log.warn("点击量统计失败 shortCode={}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * 查询短链的实时总点击量 = 数据库已落库的 + Redis 里尚未落库的增量。
     */
    public long getClickCount(String shortCode){
        ShortLink shortlink = shortLinkRepository.findByShortCode(shortCode);
        if (shortlink == null){
            throw new IllegalArgumentException("Short code not found: " + shortCode);
        }
        long persisted = shortlink.getClickCount() == null ? 0L : shortlink.getClickCount();

        long pending = 0L;
        try {
            String value = redisTemplate.opsForValue().get("click:" + shortCode);
            if (value != null){
                pending = Long.parseLong(value);
            }
        } catch (Exception e){
            // Redis 不可用：只返回已落库的部分
            log.warn("读取实时点击增量失败 shortCode={}: {}", shortCode, e.getMessage());
        }
        return persisted + pending;
    }

    /**
     * 查询短链详情（原始 URL、状态、点击量、过期时间等）。
     */
    public ShortLinkDetailResponse findShortLink(String shortCode){
        ShortLink shortlink = shortLinkRepository.findByShortCode(shortCode);
        if (shortlink == null){
            throw new IllegalArgumentException("Short code not found: " + shortCode);
        }
        return new ShortLinkDetailResponse(
                shortlink.getShortCode(),
                shortlink.getOriginalUrl(),
                shortlink.getStatus(),
                getClickCount(shortCode),
                shortlink.getCreatedAt(),
                shortlink.getExpirationDate()
        );
    }

    /**
     * 软删除短链：把状态标记为 deleted（不物理删行，保留历史与统计）。
     * 同时清掉 Redis 缓存，避免删除后仍能通过缓存跳转。
     * 注意：布隆过滤器无法删除元素，被删短码仍会通过布隆，但后续 status 校验会拦下，无副作用。
     */
    @Transactional
    public void deleteShortLink(String shortCode){
        ShortLink shortlink = shortLinkRepository.findByShortCode(shortCode);
        if (shortlink == null){
            throw new IllegalArgumentException("Short code not found: " + shortCode);
        }
        shortlink.setStatus("deleted");
        shortLinkRepository.save(shortlink);

        // 清缓存：事务提交后再清，避免回滚了缓存却已被删
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit(){
                // 清本地缓存（仅当前实例；多实例下其它实例靠 Caffeine 短 TTL 自然过期）
                shortLinkCache.invalidate(shortCode);
                try {
                    redisTemplate.delete("shortlink:" + shortCode);
                } catch (Exception e){
                    log.warn("删除短链后清缓存失败 shortCode={}: {}", shortCode, e.getMessage());
                }
            }
        });
    }
}
