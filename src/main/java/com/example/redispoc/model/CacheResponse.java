package com.example.redispoc.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CacheResponse {
    private boolean success;
    private String message;
    private Object data;

    public static CacheResponse success(String message, Object data) {
        return new CacheResponse(true, message, data);
    }

    public static CacheResponse success(String message) {
        return new CacheResponse(true, message, null);
    }

    public static CacheResponse error(String message) {
        return new CacheResponse(false, message, null);
    }
}
