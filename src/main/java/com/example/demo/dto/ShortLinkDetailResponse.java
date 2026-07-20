package com.example.demo.dto;

import java.time.OffsetDateTime;

public class ShortLinkDetailResponse {
    private String shortCode;
    private String originalUrl;
    private String status;
    private long clickCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime expirationDate;

    public ShortLinkDetailResponse(String shortCode, String originalUrl, String status,
                                   long clickCount, OffsetDateTime createdAt, OffsetDateTime expirationDate) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.status = status;
        this.clickCount = clickCount;
        this.createdAt = createdAt;
        this.expirationDate = expirationDate;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getStatus() {
        return status;
    }

    public long getClickCount() {
        return clickCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpirationDate() {
        return expirationDate;
    }
}
