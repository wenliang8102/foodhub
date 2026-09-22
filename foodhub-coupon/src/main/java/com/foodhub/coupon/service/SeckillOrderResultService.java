package com.foodhub.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodhub.common.messaging.SeckillOrderCreatedEvent;
import com.foodhub.coupon.entity.SeckillRequestEntity;
import com.foodhub.coupon.mapper.SeckillRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class SeckillOrderResultService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private final SeckillRequestMapper requestMapper;

    public SeckillOrderResultService(SeckillRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    @Transactional
    public void apply(SeckillOrderCreatedEvent event) {
        SeckillRequestEntity request = requestMapper.selectOne(
                new LambdaQueryWrapper<SeckillRequestEntity>()
                        .eq(SeckillRequestEntity::getRequestId, event.requestId())
                        .eq(SeckillRequestEntity::getUserId, event.userId())
                        .eq(SeckillRequestEntity::getActivityId, event.activityId()));
        if (request == null) {
            throw new IllegalStateException("Matching seckill request was not found");
        }
        if ("ORDER_CREATED".equals(request.getStatus())
                && Objects.equals(request.getOrderNo(), event.orderNo())) {
            return;
        }
        int updated = requestMapper.markOrderCreated(
                event.requestId(), event.userId(), event.activityId(), event.orderNo(),
                LocalDateTime.now(DATABASE_ZONE));
        if (updated != 1) {
            throw new IllegalStateException("Seckill request cannot accept order result");
        }
    }
}
