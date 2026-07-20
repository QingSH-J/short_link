package com.example.demo.dto;

public class ClickStatsResponse {
    private String shortCode;
    private long clickCount;

    public ClickStatsResponse(String shortCode, long clickCount) {
        this.shortCode = shortCode;
        this.clickCount = clickCount;
    }

    public String getShortCode() {
        return shortCode;
    }

    public long getClickCount() {
        return clickCount;
    }
}
