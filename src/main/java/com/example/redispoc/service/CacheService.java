package com.example.redispoc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * データを保存（有効期限なし）
     */
    public void save(String key, Object value) {
        log.info("Saving to Redis: key={}", key);
        redisTemplate.opsForValue().set(key, value);
        log.info("Successfully saved to Redis: key={}", key);
    }

    /**
     * データを保存（有効期限付き）
     */
    public void saveWithExpiry(String key, Object value, long timeout, TimeUnit unit) {
        log.info("Saving to Redis with expiry: key={}, timeout={} {}", key, timeout, unit);
        redisTemplate.opsForValue().set(key, value, timeout, unit);
        log.info("Successfully saved to Redis with expiry: key={}", key);
    }

    /**
     * データを取得
     */
    public Object get(String key) {
        log.info("Getting from Redis: key={}", key);
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.info("Found in Redis: key={}", key);
        } else {
            log.info("Not found in Redis: key={}", key);
        }
        return value;
    }

    /**
     * データを削除
     */
    public void delete(String key) {
        log.info("Deleting from Redis: key={}", key);
        Boolean deleted = redisTemplate.delete(key);
        log.info("Delete result for key={}: {}", key, deleted);
    }

    /**
     * キーの存在確認
     */
    public boolean exists(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        log.info("Checking existence in Redis: key={}, exists={}", key, exists);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 有効期限を設定
     */
    public void setExpire(String key, long timeout, TimeUnit unit) {
        log.info("Setting expiry for key={}: {} {}", key, timeout, unit);
        Boolean result = redisTemplate.expire(key, timeout, unit);
        log.info("Set expiry result for key={}: {}", key, result);
    }

    /**
     * 残り有効期限を取得（秒）
     */
    public Long getExpire(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        log.info("TTL for key={}: {} seconds", key, ttl);
        return ttl;
    }
}
