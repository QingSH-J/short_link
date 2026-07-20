package com.example.demo.dto;

public class GetShortLinkResponse {
    private String shortCode;
    private String originalUrl;

    public GetShortLinkResponse(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}
