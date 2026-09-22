package com.foodhub.order.messaging;

import com.foodhub.common.messaging.RabbitMqContracts;
import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.service.OrderCreationService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SeckillOrderCreateListener {

    private final OrderCreationService orderCreationService;
    private final SeckillOrderCreatedPublisher createdPublisher;
    private final RabbitMessageRetry retry;

    public SeckillOrderCreateListener(OrderCreationService orderCreationService,
                                      SeckillOrderCreatedPublisher createdPublisher,
                                      RabbitMessageRetry retry) {
        this.orderCreationService = orderCreationService;
        this.createdPublisher = createdPublisher;
        this.retry = retry;
    }

    @RabbitListener(queues = RabbitMqContracts.SECKILL_ORDER_CREATE_QUEUE)
    public void handle(SeckillOrderCreateCommand command, Message message, Channel channel)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            OrderEntity order = orderCreationService.createIdempotently(command);
            if (!createdPublisher.publish(command, order)) {
                throw new IllegalStateException("Order-created event was not confirmed");
            }
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            if (retry.forward(command, message,
                    RabbitMqContracts.SECKILL_ORDER_CREATE_ROUTING_KEY)) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
