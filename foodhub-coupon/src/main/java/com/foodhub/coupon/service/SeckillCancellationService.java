package com.foodhub.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodhub.common.messaging.OrderCancelledEvent;
import com.foodhub.coupon.entity.SeckillCancellationEntity;
import com.foodhub.coupon.entity.SeckillRequestEntity;
import com.foodhub.coupon.mapper.SeckillActivityMapper;
import com.foodhub.coupon.mapper.SeckillCancellationMapper;
import com.foodhub.coupon.mapper.SeckillRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class SeckillCancellationService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final SeckillRequestMapper requestMapper;
    private final SeckillActivityMapper activityMapper;
    private final SeckillCancellationMapper cancellationMapper;
    private final SeckillRedisService redisService;

    public SeckillCancellationService(SeckillRequestMapper requestMapper,
                                      SeckillActivityMapper activityMapper,
                                      SeckillCancellationMapper cancellationMapper,
                                      SeckillRedisService redisService) {
        this.requestMapper = requestMapper;
        this.activityMapper = activityMapper;
        this.cancellationMapper = cancellationMapper;
        this.redisService = redisService;
    }

    @Transactional
    public void restore(OrderCancelledEvent event) {
        SeckillCancellationEntity processed = cancellationMapper.selectOne(
                new LambdaQueryWrapper<SeckillCancellationEntity>()
                        .eq(SeckillCancellationEntity::getOrderNo, event.orderNo()));
        if (processed != null) {
            return;
        }

        SeckillRequestEntity request = requestMapper.selectOne(
                new LambdaQueryWrapper<SeckillRequestEntity>()
                        .eq(SeckillRequestEntity::getOrderNo, event.orderNo())
                        .eq(SeckillRequestEntity::getUserId, event.userId())
                        .eq(SeckillRequestEntity::getActivityId, event.activityId()));
        if (request == null || !"ORDER_CREATED".equals(request.getStatus())) {
            throw new IllegalStateException("Matching active seckill request was not found");
        }

        redisService.rollback(event.activityId(), event.userId(), request.getRequestId());
        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);
        if (activityMapper.restoreStock(event.activityId(), event.quantity(), now) != 1) {
            throw new IllegalStateException("Seckill stock cannot be restored");
        }
        if (requestMapper.markCancelled(event.orderNo(), event.userId(), event.activityId(),
                event.reason(), now) != 1) {
            throw new IllegalStateException("Seckill request cannot be cancelled");
        }

        SeckillCancellationEntity cancellation = new SeckillCancellationEntity();
        cancellation.setMessageId(event.messageId());
        cancellation.setOrderNo(event.orderNo());
        cancellation.setRequestId(request.getRequestId());
        cancellation.setActivityId(event.activityId());
        cancellation.setUserId(event.userId());
        cancellation.setQuantity(event.quantity());
        cancellation.setProcessedAt(now);
        cancellationMapper.insert(cancellation);
    }
}
