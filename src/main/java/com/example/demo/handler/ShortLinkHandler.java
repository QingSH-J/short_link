package com.example.demo.handler;

import java.net.URI;

import org.redisson.api.RRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ClickStatsResponse;
import com.example.demo.dto.CreateShortLinkRequest;
import com.example.demo.dto.CreateShortLinkResponse;
import com.example.demo.dto.GetShortLinkResponse;
import com.example.demo.dto.ShortLinkDetailResponse;
import com.example.demo.service.ShortLinkService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



@RestController
public class ShortLinkHandler {
    // 号段标识由服务端固定，不由客户端传入
    private static final String BIG_TAG = "short_link";

    private final ShortLinkService shortLinkService;

    private final RRateLimiter createLimiter;
    private final RRateLimiter getLimiter;

    public ShortLinkHandler(ShortLinkService shortLinkService, @Qualifier("createLimiter") RRateLimiter createLimiter, @Qualifier("getLimiter") RRateLimiter getLimiter) {
        this.shortLinkService = shortLinkService;
        this.createLimiter = createLimiter;
        this.getLimiter = getLimiter;
    }

    @PostMapping("/api/link")
    public ResponseEntity<CreateShortLinkResponse> createShortLink(@Valid @RequestBody CreateShortLinkRequest request){
        if(!createLimiter.tryAcquire()){
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String originalUrl = request.getOriginal();
        return ResponseEntity.ok(shortLinkService.createShortLink(originalUrl, BIG_TAG));
    }

    // 查询点击量：放在 /api/link 前缀下，避免与根路径 /{shortCode} 跳转冲突
    @GetMapping("/api/link/{shortCode}/stats")
    public ResponseEntity<ClickStatsResponse> stats(@PathVariable String shortCode){
        long count = shortLinkService.getClickCount(shortCode);
        return ResponseEntity.ok(new ClickStatsResponse(shortCode, count));
    }

    // 查询短链详情
    @GetMapping("/api/link/{shortCode}")
    public ResponseEntity<ShortLinkDetailResponse> find(@PathVariable String shortCode){
        return ResponseEntity.ok(shortLinkService.findShortLink(shortCode));
    }

    // 软删除短链
    @DeleteMapping("/api/link/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode){
        shortLinkService.deleteShortLink(shortCode);
        return ResponseEntity.noContent().build();   // 204
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {

        if(!getLimiter.tryAcquire()){
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
    
        GetShortLinkResponse resp = shortLinkService.getShortLink(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)          // 302
                .location(URI.create(resp.getOriginalUrl()))    // Location 头
                .build();
}
}
