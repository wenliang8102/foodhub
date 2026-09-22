package com.foodhub.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.order.entity.ConsumedMessageEntity;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.mapper.ConsumedMessageMapper;
import com.foodhub.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class OrderCreationService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderMapper orderMapper;
    private final ConsumedMessageMapper consumedMessageMapper;

    public OrderCreationService(OrderMapper orderMapper,
                                ConsumedMessageMapper consumedMessageMapper) {
        this.orderMapper = orderMapper;
        this.consumedMessageMapper = consumedMessageMapper;
    }

    @Transactional
    public OrderEntity createIdempotently(SeckillOrderCreateCommand command) {
        OrderEntity existing = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getRequestId, command.requestId()));
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);
        OrderEntity order = new OrderEntity();
        order.setOrderNo(newOrderNo());
        order.setRequestId(command.requestId());
        order.setUserId(command.userId());
        order.setActivityId(command.activityId());
        order.setTargetType(command.targetType().name());
        order.setTargetId(command.targetId());
        order.setQuantity(command.quantity());
        order.setUnitPrice(command.unitPrice());
        order.setTotalAmount(command.unitPrice().multiply(BigDecimal.valueOf(command.quantity())));
        order.setStatus("PENDING_PAY");
        order.setPaymentExpiresAt(LocalDateTime.ofInstant(
                command.paymentExpiresAt(), DATABASE_ZONE));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        ConsumedMessageEntity consumed = new ConsumedMessageEntity();
        consumed.setMessageId(command.messageId());
        consumed.setRequestId(command.requestId());
        consumed.setConsumedAt(now);
        consumedMessageMapper.insert(consumed);
        return order;
    }

    private String newOrderNo() {
        return "FH" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
