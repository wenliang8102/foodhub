package com.foodhub.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.social.config.SocialCacheProperties;
import com.foodhub.social.vo.PostView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SocialPostCache {

    private static final String NULL_VALUE = "__NULL__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SocialCacheProperties properties;
    private final SocialMetrics metrics;

    public SocialPostCache(ObjectProvider<StringRedisTemplate> redisTemplate,
                           ObjectMapper objectMapper,
                           SocialCacheProperties properties,
                           SocialMetrics metrics) {
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    public Optional<PostView> getPostDetail(long postId) {
        if (redisTemplate == null) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(detailKey(postId));
            if (value == null || NULL_VALUE.equals(value)) {
                metrics.increment("social.post.cache.lookup", "result", value == null ? "miss" : "null");
                return Optional.empty();
            }
            metrics.increment("social.post.cache.lookup", "result", "hit");
            return Optional.of(objectMapper.readValue(value, PostView.class));
        } catch (Exception exception) {
            metrics.increment("social.post.cache.lookup", "result", "fallback");
            return Optional.empty();
        }
    }

    public boolean isNullCached(long postId) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            boolean hit = NULL_VALUE.equals(redisTemplate.opsForValue().get(nullKey(postId)));
            if (hit) {
                metrics.increment("social.post.cache.lookup", "result", "null");
            }
            return hit;
        } catch (Exception exception) {
            metrics.increment("social.post.cache.lookup", "result", "fallback");
            return false;
        }
    }

    public void putPostDetail(PostView postView) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    detailKey(postView.id()),
                    objectMapper.writeValueAsString(postView),
                    ttl(properties.getPostDetailTtlSeconds(), properties.getPostDetailJitterSeconds()));
        } catch (JsonProcessingException exception) {
            metrics.increment("social.post.cache.write", "result", "serialize_error");
        } catch (Exception exception) {
            metrics.increment("social.post.cache.write", "result", "fallback");
        }
    }

    public void putNull(long postId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    nullKey(postId), NULL_VALUE, Duration.ofSeconds(properties.getNullTtlSeconds()));
        } catch (Exception exception) {
            metrics.increment("social.post.cache.write", "result", "fallback");
        }
    }

    public void evictPost(long postId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(detailKey(postId));
            redisTemplate.delete(nullKey(postId));
        } catch (Exception exception) {
            metrics.increment("social.post.cache.evict", "result", "fallback");
        }
    }

    public String detailKey(long postId) {
        return "foodhub:post:detail:" + postId;
    }

    public String nullKey(long postId) {
        return "foodhub:post:null:" + postId;
    }

    public String countKey(String kind, long postId) {
        return "foodhub:post:" + kind + ":" + postId;
    }

    private Duration ttl(int baseSeconds, int jitterSeconds) {
        int jitter = jitterSeconds <= 0 ? 0 : ThreadLocalRandom.current().nextInt(jitterSeconds + 1);
        return Duration.ofSeconds(baseSeconds + jitter);
    }
}
