package com.foodhub.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.mapper.OrderMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class OrderTimeoutScheduler {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderMapper orderMapper;
    private final OrderCancellationService cancellationService;

    public OrderTimeoutScheduler(OrderMapper orderMapper,
                                 OrderCancellationService cancellationService) {
        this.orderMapper = orderMapper;
        this.cancellationService = cancellationService;
    }

    @Scheduled(fixedDelayString = "${foodhub.order.timeout-scan-delay-ms:5000}")
    public void cancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);
        List<OrderEntity> expired = orderMapper.selectList(
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getStatus, "PENDING_PAY")
                        .le(OrderEntity::getPaymentExpiresAt, now)
                        .orderByAsc(OrderEntity::getId)
                        .last("LIMIT 50"));
        expired.forEach(order -> cancellationService.cancelExpired(order, now));
    }
}
