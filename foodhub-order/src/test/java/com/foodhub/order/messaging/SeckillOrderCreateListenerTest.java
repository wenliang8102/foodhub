package com.foodhub.order.messaging;

import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.common.messaging.SeckillTargetType;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.service.OrderCreationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillOrderCreateListenerTest {

    private final OrderCreationService orderCreationService = mock(OrderCreationService.class);
    private final SeckillOrderCreatedPublisher createdPublisher = mock(SeckillOrderCreatedPublisher.class);
    private final RabbitMessageRetry retry = mock(RabbitMessageRetry.class);
    private final SeckillOrderCreateListener listener =
            new SeckillOrderCreateListener(orderCreationService, createdPublisher, retry);

    @Test
    void shouldAckAfterCreatedEventIsConfirmed() throws Exception {
        SeckillOrderCreateCommand command = command();
        OrderEntity order = new OrderEntity();
        Channel channel = mock(Channel.class);
        Message message = message(77L);
        when(orderCreationService.createIdempotently(command)).thenReturn(order);
        when(createdPublisher.publish(command, order)).thenReturn(true);

        listener.handle(command, message, channel);

        verify(channel).basicAck(77L, false);
        verify(channel, never()).basicNack(77L, false, true);
    }

    @Test
    void shouldRequeueWhenCreatedEventIsNotConfirmed() throws Exception {
        SeckillOrderCreateCommand command = command();
        OrderEntity order = new OrderEntity();
        Channel channel = mock(Channel.class);
        Message message = message(88L);
        when(orderCreationService.createIdempotently(command)).thenReturn(order);
        when(createdPublisher.publish(command, order)).thenReturn(false);

        listener.handle(command, message, channel);

        verify(channel).basicNack(88L, false, true);
        verify(channel, never()).basicAck(88L, false);
    }

    private Message message(long deliveryTag) {
        Message message = new Message(new byte[0]);
        message.getMessageProperties().setDeliveryTag(deliveryTag);
        return message;
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
                1,
                new BigDecimal("19.90"),
                occurredAt,
                occurredAt.plusSeconds(900));
    }
}
