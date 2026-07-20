package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 号段表 ids 的实体，用于分布式 ID 号段分配。
 * 取号请使用原子 UPDATE（max_id = max_id + step），不要用读-改-写。
 */
@Entity
@Table(name = "ids")
public class IdSegment {

    @Id
    @Column(name = "big_tag", nullable = false, length = 255)
    private String bigTag;

    @Column(name = "max_id", nullable = false)
    private Long maxId;

    @Column(name = "step", nullable = false)
    private Long step;

    public String getBigTag() {
        return bigTag;
    }

    public void setBigTag(String bigTag) {
        this.bigTag = bigTag;
    }

    public Long getMaxId() {
        return maxId;
    }

    public void setMaxId(Long maxId) {
        this.maxId = maxId;
    }

    public Long getStep() {
        return step;
    }

    public void setStep(Long step) {
        this.step = step;
    }
}
