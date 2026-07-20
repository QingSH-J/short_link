package com.example.demo.dto;

public class CreateShortLinkRequest {
    private String original;
    private String bigTag;

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public String getBigTag() {
        return bigTag;
    }

    public void setBigTag(String bigTag) {
        this.bigTag = bigTag;
    }
}
