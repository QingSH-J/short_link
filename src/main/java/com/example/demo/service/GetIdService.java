package com.example.demo.service;

import org.springframework.stereotype.Service;

import com.example.demo.entity.IdSegment;
import com.example.demo.repository.IdRepository;




@Service
public class GetIdService {
    private final IdRepository idRepository;
    private long currentId = 0;
    private long maxId = -1;

    public GetIdService(IdRepository idRepository) {
        this.idRepository = idRepository;
    }

    public synchronized long getId(String bigTag) {
        if (currentId <= maxId){
            return currentId++;
        }else{
            // 原子占段：一条 UPDATE ... RETURNING 直接拿到更新后的号段，
            // 内存区间完全等于数据库真正占到的那一段，并发也不会重叠。
            IdSegment idSegment = idRepository.increment(bigTag);
            if (idSegment == null) {
                throw new RuntimeException("Failed to find IdSegment for bigTag: " + bigTag);
            }

            maxId = idSegment.getMaxId();
            currentId = maxId - idSegment.getStep() + 1;
        }
        return currentId++;

    }
}      
