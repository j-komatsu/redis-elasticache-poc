package com.example.redispoc.model;

import lombok.Data;

@Data
public class CacheRequest {
    private String key;
    private String value;
    private Long ttlSeconds; // 有効期限（秒）、nullの場合は無期限
}
