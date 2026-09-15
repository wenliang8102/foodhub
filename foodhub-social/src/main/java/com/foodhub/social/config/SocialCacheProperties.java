package com.foodhub.social.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "foodhub.social.cache")
public class SocialCacheProperties {

    private int postDetailTtlSeconds = 300;
    private int postDetailJitterSeconds = 60;
    private int counterTtlSeconds = 60;
    private int counterJitterSeconds = 15;
    private int nullTtlSeconds = 30;

    public int getPostDetailTtlSeconds() { return postDetailTtlSeconds; }
    public void setPostDetailTtlSeconds(int postDetailTtlSeconds) {
        this.postDetailTtlSeconds = postDetailTtlSeconds;
    }
    public int getPostDetailJitterSeconds() { return postDetailJitterSeconds; }
    public void setPostDetailJitterSeconds(int postDetailJitterSeconds) {
        this.postDetailJitterSeconds = postDetailJitterSeconds;
    }
    public int getCounterTtlSeconds() { return counterTtlSeconds; }
    public void setCounterTtlSeconds(int counterTtlSeconds) {
        this.counterTtlSeconds = counterTtlSeconds;
    }
    public int getCounterJitterSeconds() { return counterJitterSeconds; }
    public void setCounterJitterSeconds(int counterJitterSeconds) {
        this.counterJitterSeconds = counterJitterSeconds;
    }
    public int getNullTtlSeconds() { return nullTtlSeconds; }
    public void setNullTtlSeconds(int nullTtlSeconds) { this.nullTtlSeconds = nullTtlSeconds; }
}
