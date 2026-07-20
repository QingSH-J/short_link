package com.example.demo.dto;

public class CreateShortLinkResponse {
    public String shortCode;
    public String originalUrl;

    public CreateShortLinkResponse(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }
}
