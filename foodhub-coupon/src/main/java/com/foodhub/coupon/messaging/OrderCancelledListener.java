package com.foodhub.coupon.messaging;

import com.foodhub.common.messaging.OrderCancelledEvent;
import com.foodhub.common.messaging.RabbitMqContracts;
import com.foodhub.coupon.service.SeckillCancellationService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderCancelledListener {

    private final SeckillCancellationService cancellationService;
    private final RabbitMessageRetry retry;

    public OrderCancelledListener(SeckillCancellationService cancellationService,
                                  RabbitMessageRetry retry) {
        this.cancellationService = cancellationService;
        this.retry = retry;
    }

    @RabbitListener(queues = RabbitMqContracts.ORDER_CANCELLED_QUEUE)
    public void handle(OrderCancelledEvent event, Message message, Channel channel)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            cancellationService.restore(event);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            if (retry.forward(event, message, RabbitMqContracts.ORDER_CANCELLED_ROUTING_KEY)) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
