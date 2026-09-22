package com.foodhub.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodhub.common.core.BusinessException;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.mapper.OrderMapper;
import com.foodhub.order.vo.OrderView;
import com.foodhub.order.vo.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Service
public class OrderQueryService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private final OrderMapper orderMapper;

    public OrderQueryService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional(readOnly = true)
    public OrderView detail(long userId, String orderNo) {
        OrderEntity order = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getOrderNo, orderNo)
                .eq(OrderEntity::getUserId, userId));
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order does not exist");
        }
        return view(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderView> list(long userId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page must be at least 1 and size must be 1 to 100");
        }
        Page<OrderEntity> result = orderMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getUserId, userId)
                        .orderByDesc(OrderEntity::getCreatedAt)
                        .orderByDesc(OrderEntity::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::view).toList(),
                page, size, result.getTotal());
    }

    private OrderView view(OrderEntity order) {
        return new OrderView(
                order.getId(), order.getOrderNo(), order.getRequestId(), order.getUserId(),
                order.getActivityId(), order.getTargetType(), order.getTargetId(),
                order.getQuantity(), order.getUnitPrice(), order.getTotalAmount(), order.getStatus(),
                order.getPaymentExpiresAt().atZone(DATABASE_ZONE).toOffsetDateTime(),
                toOffset(order.getPaidAt()),
                toOffset(order.getCancelledAt()),
                order.getCreatedAt().atZone(DATABASE_ZONE).toOffsetDateTime());
    }

    private java.time.OffsetDateTime toOffset(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(DATABASE_ZONE).toOffsetDateTime();
    }
}
