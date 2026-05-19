package com.payorch.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyManager {

    private final StringRedisTemplate redisTemplate;

    // Prefixes to keep Redis organized
    private static final String LOCK_KEY_PREFIX = "lock:payment:";
    private static final String RESP_KEY_PREFIX = "resp:payment:";

    /**
     * Attempts to acquire an atomic lock for a specific request.
     * @param key The idempotency key from the client.
     * @return true if lock acquired, false if another thread/instance is already processing.
     */
    public boolean acquireLock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        
        // SET IF ABSENT (NX) with a TTL (EX) of 5 minutes.
        // This ensures that if the server crashes during processing, 
        // the lock will eventually expire and the user can try again.
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey, 
                "PROCESSING", 
                Duration.ofMinutes(5)
        );

        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Stores the final processed response in Redis.
     * Once the response is saved, we delete the temporary 'processing' lock.
     */
    public void saveResponse(String key, String responseJson) {
        String respKey = RESP_KEY_PREFIX + key;
        String lockKey = LOCK_KEY_PREFIX + key;

        // Store response for 24 hours (standard for payment systems)
        redisTemplate.opsForValue().set(respKey, responseJson, Duration.ofHours(24));
        
        // Remove the lock so it doesn't wait for TTL
        redisTemplate.delete(lockKey);
        
        log.info("Saved final response for idempotency key: {}", key);
    }

    /**
     * Checks if we have already finished processing this request.
     */
    public String getResponse(String key) {
        return redisTemplate.opsForValue().get(RESP_KEY_PREFIX + key);
    }

    /**
     * Utility to release lock in case of a fatal error before a response is generated.
     */
    public void releaseLock(String key) {
        redisTemplate.delete(LOCK_KEY_PREFIX + key);
    }
}