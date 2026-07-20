package com.example.demo.util;

public class Encode {
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = BASE62.length;

    public static String encodeBase62(long value){
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }

        if (value == 0) {
            return String.valueOf(BASE62[0]);
        }

        StringBuilder sb = new StringBuilder();

        while(value > 0){
            int remainder = (int)(value % BASE);
            sb.append(BASE62[remainder]);
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decodeBase62(String value){
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Value cannot be null or empty");
        }

        long result = 0;

        for (int i = 0; i < value.length(); i++){
            int index = indexOf(value.charAt(i));

            if (index < 0){
                throw new IllegalArgumentException("Invalid character in Base62 string: " + value.charAt(i));
            }

            try {
                result = Math.addExact(Math.multiplyExact(result, BASE), index);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Base62 string is too long: " + value);
            }
        }

        return result;
    }

    private static int indexOf(char c){
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        } else if (c >= 'a' && c <= 'z') {
            return c - 'a' + 36;
        } 

        return -1;
    }
}

