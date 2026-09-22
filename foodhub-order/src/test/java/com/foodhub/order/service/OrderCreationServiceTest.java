package com.foodhub.order.service;

import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.common.messaging.SeckillTargetType;
import com.foodhub.order.entity.ConsumedMessageEntity;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.mapper.ConsumedMessageMapper;
import com.foodhub.order.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreationServiceTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final ConsumedMessageMapper consumedMessageMapper = mock(ConsumedMessageMapper.class);
    private final OrderCreationService service = new OrderCreationService(orderMapper, consumedMessageMapper);

    @Test
    void shouldCreatePendingPaymentOrderAndRecordConsumedMessage() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        SeckillOrderCreateCommand command = command();

        OrderEntity result = service.createIdempotently(command);

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderMapper).insert(orderCaptor.capture());
        OrderEntity inserted = orderCaptor.getValue();
        assertThat(result).isSameAs(inserted);
        assertThat(inserted.getOrderNo()).startsWith("FH");
        assertThat(inserted.getRequestId()).isEqualTo(command.requestId());
        assertThat(inserted.getStatus()).isEqualTo("PENDING_PAY");
        assertThat(inserted.getQuantity()).isEqualTo(2);
        assertThat(inserted.getTotalAmount()).isEqualByComparingTo("39.80");

        ArgumentCaptor<ConsumedMessageEntity> consumedCaptor =
                ArgumentCaptor.forClass(ConsumedMessageEntity.class);
        verify(consumedMessageMapper).insert(consumedCaptor.capture());
        assertThat(consumedCaptor.getValue().getMessageId()).isEqualTo(command.messageId());
        assertThat(consumedCaptor.getValue().getRequestId()).isEqualTo(command.requestId());
    }

    @Test
    void shouldReturnExistingOrderForRepeatedRequest() {
        OrderEntity existing = new OrderEntity();
        existing.setOrderNo("FH-EXISTING");
        when(orderMapper.selectOne(any())).thenReturn(existing);

        OrderEntity result = service.createIdempotently(command());

        assertThat(result).isSameAs(existing);
        verify(orderMapper, never()).insert(any(OrderEntity.class));
        verify(consumedMessageMapper, never()).insert(any(ConsumedMessageEntity.class));
    }

    private SeckillOrderCreateCommand command() {
        Instant occurredAt = Instant.parse("2026-09-20T10:00:00Z");
        return new SeckillOrderCreateCommand(
                "message-1",
                "request-1",
                101L,
                201L,
                SeckillTargetType.COUPON,
                301L,
                2,
                new BigDecimal("19.90"),
                occurredAt,
                occurredAt.plusSeconds(900));
    }
}
