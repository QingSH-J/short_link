package com.example.demo.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.entity.IdSegment;
import com.example.demo.repository.IdRepository;

import jakarta.annotation.PreDestroy;

/**
 * 号段发号器（参考美团 Leaf-Segment 双 buffer 方案）。
 *
 * 核心：数据库批量取号 + 内存分发，减少数据库访问到 1/step。
 * 双 buffer：当前号段用到 90% 时，异步预加载下一段到备用 buffer；
 *           当前段用尽时无缝切换到已备好的下一段，消除换段时的 DB 等待毛刺。
 * 多 tag：每个 bigTag 各自维护一套双 buffer（ConcurrentHashMap）。
 */
@Service
public class GetIdService {
    private static final Logger log = LoggerFactory.getLogger(GetIdService.class);
    // 当前段消耗到 90% 时触发下一段的异步预加载
    private static final double PRELOAD_THRESHOLD = 0.9;

    private final IdRepository idRepository;
    // 每个 bigTag 一套双 buffer
    private final ConcurrentHashMap<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();
    // 异步预加载线程池
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "segment-preload");
        t.setDaemon(true);
        return t;
    });

    public GetIdService(IdRepository idRepository) {
        this.idRepository = idRepository;
    }

    public long getId(String bigTag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(bigTag, SegmentBuffer::new);
        return buffer.nextId();
    }

    @PreDestroy
    public void shutdown() {
        preloadExecutor.shutdown();
    }

    /**
     * 从数据库原子占用一个号段：max_id += step，返回更新后的号段区间。
     */
    private Segment loadSegment(String bigTag) {
        IdSegment idSegment = idRepository.increment(bigTag);
        if (idSegment == null) {
            throw new RuntimeException("Failed to find IdSegment for bigTag: " + bigTag);
        }
        long maxId = idSegment.getMaxId();
        long start = maxId - idSegment.getStep() + 1;
        return new Segment(start, maxId);
    }

    /**
     * 一个号段区间 [start, maxId]，currentId 为下一个要发的号。
     */
    private static class Segment {
        final AtomicLong currentId;
        final long maxId;
        final long start;

        Segment(long start, long maxId) {
            this.start = start;
            this.currentId = new AtomicLong(start);
            this.maxId = maxId;
        }

        long length() {
            return maxId - start + 1;
        }
    }

    /**
     * 单个 bigTag 的双 buffer 容器。
     */
    private class SegmentBuffer {
        private final String bigTag;
        private final Segment[] segments = new Segment[2];  // 双 buffer
        private volatile int currentPos = 0;                // 当前用哪个（0/1）
        private volatile boolean nextReady = false;         // 下一段是否已备好
        private final AtomicBoolean isPreloading = new AtomicBoolean(false); // 是否正在异步加载
        private final Object switchLock = new Object();      // 换段时的互斥锁

        SegmentBuffer(String bigTag) {
            this.bigTag = bigTag;
            // 首次同步加载第一段
            this.segments[0] = loadSegment(bigTag);
        }

        long nextId() {
            while (true) {
                Segment current = segments[currentPos];
                long value = current.currentId.getAndIncrement();

                if (value <= current.maxId) {
                    // 消耗到阈值且下一段还没准备 → 触发异步预加载
                    if (!nextReady
                            && (value - current.start) >= current.length() * PRELOAD_THRESHOLD
                            && isPreloading.compareAndSet(false, true)) {
                        preloadNextSegment();
                    }
                    return value;
                }
                // 当前段已用尽，切换到下一段
                switchToNextSegment();
            }
        }

        /**
         * 异步把下一段加载进备用 buffer。
         */
        private void preloadNextSegment() {
            preloadExecutor.submit(() -> {
                try {
                    Segment next = loadSegment(bigTag);
                    segments[nextPos()] = next;
                    nextReady = true;
                } catch (Exception e) {
                    // 预加载失败：重置标记，下次取号会重试（此时可能同步加载）
                    isPreloading.set(false);
                    log.warn("号段预加载失败 bigTag={}: {}", bigTag, e.getMessage());
                }
            });
        }

        /**
         * 切换到下一段。若预加载已完成则无缝切换；否则同步加载（兜底）。
         */
        private void switchToNextSegment() {
            synchronized (switchLock) {
                // 双重检查：可能已被别的线程切换过
                Segment current = segments[currentPos];
                if (current.currentId.get() <= current.maxId) {
                    return;
                }
                if (nextReady) {
                    // 预加载已就绪，无缝切换
                    currentPos = nextPos();
                    nextReady = false;
                    isPreloading.set(false);
                } else {
                    // 兜底：预加载没跟上（流量突增/DB 慢），同步加载
                    log.warn("号段预加载未就绪，同步加载 bigTag={}", bigTag);
                    segments[nextPos()] = loadSegment(bigTag);
                    currentPos = nextPos();
                    isPreloading.set(false);
                }
            }
        }

        private int nextPos() {
            return (currentPos + 1) & 1;
        }
    }
}
