package com.example.demo.config;

import org.redisson.api.RBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.example.demo.repository.ShortLinkRepository;

@Component
public class BloomFilterInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BloomFilterInitializer.class);
    private static final int PAGE_SIZE = 1000;

    private final RBloomFilter<String> shortLinkBloomFilter;
    private final ShortLinkRepository shortLinkRepository;

    public BloomFilterInitializer(RBloomFilter<String> shortLinkBloomFilter, ShortLinkRepository shortLinkRepository) {
        this.shortLinkBloomFilter = shortLinkBloomFilter;
        this.shortLinkRepository = shortLinkRepository;
    }

    @Override
    public void run(String... args) {
        // 分页加载所有短码到布隆过滤器，避免一次性 findAll 撑爆内存
        long count = 0;
        int pageNumber = 0;
        Page<String> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
            page = shortLinkRepository.findAllShortCodes(pageable);
            for (String shortCode : page.getContent()) {
                shortLinkBloomFilter.add(shortCode);
            }
            count += page.getNumberOfElements();
            pageNumber++;
        } while (page.hasNext());

        log.info("布隆过滤器预热完成，共加载 {} 个短码", count);
    }
}
