package com.example.demo.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.ShortLink;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    ShortLink findByShortCode(String shortCode);
    List<ShortLink> findByStatus(String status);
    List<ShortLink> findByExpirationDateBefore(OffsetDateTime date);

    // 布隆预热用：只查 short_code 单列，配合分页，避免一次性把整表加载进内存
    @Query("SELECT s.shortCode FROM ShortLink s")
    Page<String> findAllShortCodes(Pageable pageable);

    // 定时任务用：把 Redis 累计的点击增量原子地加到数据库
    @Modifying
    @Query("UPDATE ShortLink s SET s.clickCount = s.clickCount + :delta WHERE s.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode, @Param("delta") long delta);
}