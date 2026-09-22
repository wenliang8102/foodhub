package com.foodhub.order.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.mapper.OrderMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaymentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-09-20T13:00:00Z");

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderCancellationService cancellationService = mock(OrderCancellationService.class);
    private final OrderPaymentService service = new OrderPaymentService(
            orderMapper, cancellationService, Clock.fixed(NOW, ZONE));

    @Test
    void shouldPayPendingOrderBeforeDeadline() {
        OrderEntity order = order("PENDING_PAY", LocalDateTime.ofInstant(NOW, ZONE).plusMinutes(5));
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.markPaidIfPayable(order.getId(), order.getUserId(),
                LocalDateTime.ofInstant(NOW, ZONE))).thenReturn(1);

        service.pay(order.getUserId(), order.getOrderNo());

        verify(orderMapper).markPaidIfPayable(order.getId(), order.getUserId(),
                LocalDateTime.ofInstant(NOW, ZONE));
        verify(cancellationService, never()).cancelExpired(any(), any());
    }

    @Test
    void shouldTreatRepeatedPaymentAsSuccess() {
        OrderEntity order = order("PAID", LocalDateTime.ofInstant(NOW, ZONE).minusMinutes(1));
        when(orderMapper.selectOne(any())).thenReturn(order);

        service.pay(order.getUserId(), order.getOrderNo());

        verify(orderMapper, never()).markPaidIfPayable(anyLong(), anyLong(), any());
        verify(cancellationService, never()).cancelExpired(any(), any());
    }

    @Test
    void shouldCancelInsteadOfPayingExpiredOrder() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZONE);
        OrderEntity order = order("PENDING_PAY", now.minusSeconds(1));
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.pay(order.getUserId(), order.getOrderNo()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("ORDER_PAYMENT_EXPIRED");
        verify(cancellationService).cancelExpired(order, now);
        verify(orderMapper, never()).markPaidIfPayable(anyLong(), anyLong(), any());
    }

    private OrderEntity order(String status, LocalDateTime expiresAt) {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setOrderNo("FH-PAYMENT");
        order.setUserId(101L);
        order.setStatus(status);
        order.setPaymentExpiresAt(expiresAt);
        return order;
    }
}
