package com.example.redispoc.controller;

import com.example.redispoc.model.CacheRequest;
import com.example.redispoc.model.CacheResponse;
import com.example.redispoc.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * データを保存
     * POST /api/cache
     * Body: {"key": "mykey", "value": "myvalue", "ttlSeconds": 300}
     */
    @PostMapping
    public ResponseEntity<CacheResponse> saveCache(@RequestBody CacheRequest request) {
        try {
            if (request.getKey() == null || request.getKey().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(CacheResponse.error("Key is required"));
            }

            if (request.getTtlSeconds() != null && request.getTtlSeconds() > 0) {
                cacheService.saveWithExpiry(
                        request.getKey(),
                        request.getValue(),
                        request.getTtlSeconds(),
                        TimeUnit.SECONDS
                );
                return ResponseEntity.ok(CacheResponse.success(
                        "Data saved with TTL: " + request.getTtlSeconds() + " seconds"
                ));
            } else {
                cacheService.save(request.getKey(), request.getValue());
                return ResponseEntity.ok(CacheResponse.success("Data saved successfully"));
            }
        } catch (Exception e) {
            log.error("Error saving to cache", e);
            return ResponseEntity.internalServerError()
                    .body(CacheResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * データを取得
     * GET /api/cache/{key}
     */
    @GetMapping("/{key}")
    public ResponseEntity<CacheResponse> getCache(@PathVariable String key) {
        try {
            Object value = cacheService.get(key);
            if (value != null) {
                return ResponseEntity.ok(CacheResponse.success("Data found", value));
            } else {
                return ResponseEntity.ok(CacheResponse.success("No data found for key: " + key, null));
            }
        } catch (Exception e) {
            log.error("Error getting from cache", e);
            return ResponseEntity.internalServerError()
                    .body(CacheResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * データを削除
     * DELETE /api/cache/{key}
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<CacheResponse> deleteCache(@PathVariable String key) {
        try {
            cacheService.delete(key);
            return ResponseEntity.ok(CacheResponse.success("Data deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting from cache", e);
            return ResponseEntity.internalServerError()
                    .body(CacheResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * キーの存在確認
     * GET /api/cache/{key}/exists
     */
    @GetMapping("/{key}/exists")
    public ResponseEntity<CacheResponse> checkExists(@PathVariable String key) {
        try {
            boolean exists = cacheService.exists(key);
            return ResponseEntity.ok(CacheResponse.success(
                    "Key exists: " + exists,
                    exists
            ));
        } catch (Exception e) {
            log.error("Error checking existence", e);
            return ResponseEntity.internalServerError()
                    .body(CacheResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * TTL（残り有効期限）を取得
     * GET /api/cache/{key}/ttl
     */
    @GetMapping("/{key}/ttl")
    public ResponseEntity<CacheResponse> getTTL(@PathVariable String key) {
        try {
            Long ttl = cacheService.getExpire(key);
            if (ttl == null || ttl < 0) {
                return ResponseEntity.ok(CacheResponse.success(
                        "No expiration set or key does not exist",
                        ttl
                ));
            } else {
                return ResponseEntity.ok(CacheResponse.success(
                        "TTL: " + ttl + " seconds",
                        ttl
                ));
            }
        } catch (Exception e) {
            log.error("Error getting TTL", e);
            return ResponseEntity.internalServerError()
                    .body(CacheResponse.error("Error: " + e.getMessage()));
        }
    }

    /**
     * ヘルスチェック
     * GET /api/cache/health
     */
    @GetMapping("/health")
    public ResponseEntity<CacheResponse> healthCheck() {
        try {
            // 簡単な書き込み・読み込みテスト
            String testKey = "health-check";
            String testValue = "OK-" + System.currentTimeMillis();
            cacheService.save(testKey, testValue);
            Object result = cacheService.get(testKey);
            cacheService.delete(testKey);

            if (testValue.equals(result)) {
                return ResponseEntity.ok(CacheResponse.success("Redis connection is healthy"));
            } else {
                return ResponseEntity.internalServerError()
                        .body(CacheResponse.error("Redis connection test failed"));
            }
        } catch (Exception e) {
            log.error("Health check failed", e);
            return ResponseEntity.internalServerError()
                    .body(CacheResponse.error("Redis connection failed: " + e.getMessage()));
        }
    }
}
