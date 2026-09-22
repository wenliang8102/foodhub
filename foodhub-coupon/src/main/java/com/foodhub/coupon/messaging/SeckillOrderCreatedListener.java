package com.foodhub.coupon.messaging;

import com.foodhub.common.messaging.RabbitMqContracts;
import com.foodhub.common.messaging.SeckillOrderCreatedEvent;
import com.foodhub.coupon.service.SeckillOrderResultService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
@Component
public class SeckillOrderCreatedListener {

    private final SeckillOrderResultService resultService;
    private final RabbitMessageRetry retry;

    public SeckillOrderCreatedListener(SeckillOrderResultService resultService,
                                       RabbitMessageRetry retry) {
        this.resultService = resultService;
        this.retry = retry;
    }

    @RabbitListener(queues = RabbitMqContracts.SECKILL_ORDER_CREATED_QUEUE)
    public void handle(SeckillOrderCreatedEvent event, Message message, Channel channel)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            resultService.apply(event);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            if (retry.forward(event, message,
                    RabbitMqContracts.SECKILL_ORDER_CREATED_ROUTING_KEY)) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
