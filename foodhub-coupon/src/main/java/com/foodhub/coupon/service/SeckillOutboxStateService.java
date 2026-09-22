package com.foodhub.coupon.service;

import com.foodhub.coupon.mapper.SeckillOutboxMapper;
import com.foodhub.coupon.mapper.SeckillRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SeckillOutboxStateService {

    private final SeckillOutboxMapper outboxMapper;
    private final SeckillRequestMapper requestMapper;

    public SeckillOutboxStateService(SeckillOutboxMapper outboxMapper,
                                     SeckillRequestMapper requestMapper) {
        this.outboxMapper = outboxMapper;
        this.requestMapper = requestMapper;
    }

    @Transactional
    public void markSent(long outboxId, String requestId, LocalDateTime now) {
        outboxMapper.markSent(outboxId, now);
        requestMapper.markMessageSent(requestId, now);
    }
}
