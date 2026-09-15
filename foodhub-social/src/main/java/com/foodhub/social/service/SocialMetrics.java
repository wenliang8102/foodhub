package com.foodhub.social.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SocialMetrics {

    private final MeterRegistry meterRegistry;

    public SocialMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.getIfAvailable();
    }

    public void increment(String name, String... tags) {
        if (meterRegistry != null) {
            meterRegistry.counter(name, tags).increment();
        }
    }

    public void recordTime(String name, Duration duration, String... tags) {
        if (meterRegistry != null) {
            Timer.builder(name).tags(tags).register(meterRegistry).record(duration);
        }
    }
}
