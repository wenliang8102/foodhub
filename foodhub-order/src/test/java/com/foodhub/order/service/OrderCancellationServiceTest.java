package com.foodhub.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.messaging.OrderCancelledEvent;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.entity.OrderOutboxEntity;
import com.foodhub.order.mapper.OrderMapper;
import com.foodhub.order.mapper.OrderOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCancellationServiceTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderOutboxMapper outboxMapper = mock(OrderOutboxMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrderCancellationService service =
            new OrderCancellationService(orderMapper, outboxMapper, objectMapper);

    @Test
    void shouldCancelExpiredOrderAndCreateOutboxEvent() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 20, 21, 0);
        OrderEntity order = order();
        when(orderMapper.cancelIfExpired(order.getId(), now)).thenReturn(1);

        boolean cancelled = service.cancelExpired(order, now);

        assertThat(cancelled).isTrue();
        ArgumentCaptor<OrderOutboxEntity> captor = ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(outboxMapper).insert(captor.capture());
        OrderOutboxEntity outbox = captor.getValue();
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getEventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(outbox.getAggregateKey()).isEqualTo(order.getOrderNo());
        OrderCancelledEvent event = objectMapper.readValue(
                outbox.getPayload(), OrderCancelledEvent.class);
        assertThat(event.orderNo()).isEqualTo(order.getOrderNo());
        assertThat(event.reason()).isEqualTo("PAYMENT_TIMEOUT");
        assertThat(event.quantity()).isEqualTo(1);
    }

    private OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setOrderNo("FH-EXPIRED");
        order.setUserId(101L);
        order.setActivityId(201L);
        order.setTargetType("COUPON");
        order.setTargetId(301L);
        order.setQuantity(1);
        return order;
    }
}
