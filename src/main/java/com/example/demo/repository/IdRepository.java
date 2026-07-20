package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.IdSegment;

public interface IdRepository extends JpaRepository<IdSegment, String> {
    IdSegment findByBigTag(String bigTag);

    /**
     * 原子地占用一个号段：max_id += step，并用 PostgreSQL 的 RETURNING
     * 直接返回更新后的整行。占段和取值在同一条语句里完成，天然原子，
     * 并发/多实例下也不会拿到重叠的号段。
     */
    @Query(value = "UPDATE ids SET max_id = max_id + step "
            + "WHERE big_tag = :bigTag RETURNING big_tag, max_id, step",
            nativeQuery = true)
    IdSegment increment(@Param("bigTag") String bigTag);

}
