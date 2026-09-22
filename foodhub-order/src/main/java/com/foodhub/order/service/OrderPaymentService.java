package com.foodhub.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class OrderPaymentService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderMapper orderMapper;
    private final OrderCancellationService cancellationService;
    private final Clock clock;

    @Autowired
    public OrderPaymentService(OrderMapper orderMapper,
                               OrderCancellationService cancellationService) {
        this(orderMapper, cancellationService, Clock.system(DATABASE_ZONE));
    }

    OrderPaymentService(OrderMapper orderMapper,
                        OrderCancellationService cancellationService,
                        Clock clock) {
        this.orderMapper = orderMapper;
        this.cancellationService = cancellationService;
        this.clock = clock;
    }

    public void pay(long userId, String orderNo) {
        OrderEntity order = find(userId, orderNo);
        if ("PAID".equals(order.getStatus())) {
            return;
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new BusinessException("ORDER_CANCELLED", "Cancelled order cannot be paid");
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw new BusinessException("ORDER_NOT_PAYABLE", "Order cannot be paid in its current state");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!order.getPaymentExpiresAt().isAfter(now)) {
            cancellationService.cancelExpired(order, now);
            throw new BusinessException("ORDER_PAYMENT_EXPIRED", "Order payment window has expired");
        }
        if (orderMapper.markPaidIfPayable(order.getId(), userId, now) == 1) {
            return;
        }

        OrderEntity latest = find(userId, orderNo);
        if ("PAID".equals(latest.getStatus())) {
            return;
        }
        if ("CANCELLED".equals(latest.getStatus())) {
            throw new BusinessException("ORDER_CANCELLED", "Cancelled order cannot be paid");
        }
        throw new BusinessException("ORDER_PAYMENT_CONFLICT", "Order status changed while paying");
    }

    private OrderEntity find(long userId, String orderNo) {
        OrderEntity order = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getOrderNo, orderNo)
                .eq(OrderEntity::getUserId, userId));
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order does not exist");
        }
        return order;
    }
}
