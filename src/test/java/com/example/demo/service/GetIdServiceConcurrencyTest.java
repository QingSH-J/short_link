package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GetIdServiceConcurrencyTest {

    @Autowired
    private GetIdService getIdService;

    @Test
    void concurrentGetId_noDuplicates() throws InterruptedException {
        int threads = 50;
        int perThread = 2000;   // 共 10万个号，跨约100次换段（step=1000）
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        ids.add(getIdService.getId("short_link"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        // 10万个号全部唯一 = 无重复
        assertEquals(threads * perThread, ids.size(),
                "发号出现重复！期望 " + (threads * perThread) + " 个唯一号，实际 " + ids.size());
        System.out.println("✅ 并发发号 " + ids.size() + " 个，全部唯一，无重复");
    }
}
