package com.example.demo.util;

import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;




@Component
public class HashidsCode {
    
    private final Hashids hashids;

    public HashidsCode(
        @Value("${app.hashids.salt}") String salt,
        @Value("${app.hashids.min-length}") int minLength
    ) {
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("Salt cannot be null or empty");
        }
        if (minLength < 0) {
            throw new IllegalArgumentException("Min length must be non-negative");
        }
        this.hashids = new Hashids(salt, minLength);
    }


    public String encode(long value){
        if (value < 0){
            throw new IllegalArgumentException("Value must be non-negative");
        }
        return hashids.encode(value);
    }

    public long decode(String hash){
        if (hash == null || hash.isEmpty()){
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }
        long[] decoded = hashids.decode(hash);
        if (decoded == null || decoded.length == 0){
            throw new IllegalArgumentException("Invalid hash: " + hash);
        }
        return decoded[0];
    }
}
