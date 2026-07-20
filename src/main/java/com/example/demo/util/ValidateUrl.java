package com.example.demo.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class ValidateUrl {
    private static final Set<String> ALLOW_SCHEMES = Set.of("http", "https");

    private ValidateUrl() {
        // Private constructor to prevent instantiation
    }

    public static URI validate(String value){
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        try {
            URI uri = new URI(value).parseServerAuthority();
            String scheme = uri.getScheme();

            Boolean isValidScheme = scheme != null && ALLOW_SCHEMES.contains(scheme.toLowerCase());

            if (!uri.isAbsolute() || !isValidScheme || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid URL: " + value);
            }

            return uri;
        }catch (URISyntaxException e){
            throw new IllegalArgumentException("Invalid URL: " + value, e);
        }
    }
}
