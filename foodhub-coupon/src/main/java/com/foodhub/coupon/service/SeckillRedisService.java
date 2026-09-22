package com.foodhub.coupon.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.common.redis.RedisKeys;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class SeckillRedisService {

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script(
            "scripts/seckill-reserve.lua");
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = script(
            "scripts/seckill-rollback.lua");

    private final StringRedisTemplate redisTemplate;

    public SeckillRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void preheat(long activityId, int stock, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(RedisKeys.seckillStock(activityId),
                    Integer.toString(stock), ttl);
            redisTemplate.delete(RedisKeys.seckillUsers(activityId));
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public boolean isReady(long activityId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.seckillStock(activityId)));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public Integer stock(long activityId) {
        try {
            String value = redisTemplate.opsForValue().get(RedisKeys.seckillStock(activityId));
            return value == null ? null : Integer.valueOf(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void savePath(long activityId, long userId, String path, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.seckillPath(activityId, userId), path, ttl);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public long reserve(long activityId, long userId, String path, String requestId,
                        Duration ttl) {
        try {
            Long result = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(
                            RedisKeys.seckillStock(activityId),
                            RedisKeys.seckillUsers(activityId),
                            RedisKeys.seckillPath(activityId, userId),
                            RedisKeys.seckillRequest(activityId, userId)),
                    Long.toString(userId), path, requestId, Long.toString(ttl.toSeconds()));
            return result == null ? -1 : result;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public String requestId(long activityId, long userId) {
        try {
            return redisTemplate.opsForValue().get(RedisKeys.seckillRequest(activityId, userId));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void rollback(long activityId, long userId, String requestId) {
        try {
            redisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    List.of(
                            RedisKeys.seckillStock(activityId),
                            RedisKeys.seckillUsers(activityId),
                            RedisKeys.seckillRequest(activityId, userId)),
                    Long.toString(userId), requestId);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "SECKILL_COMPENSATION_FAILED", "Failed to restore reserved seckill stock");
        }
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    private BusinessException unavailable() {
        return new BusinessException("SECKILL_REDIS_UNAVAILABLE", "Seckill service is temporarily unavailable");
    }
}
