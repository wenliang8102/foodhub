package com.foodhub.order.config;

import com.foodhub.common.messaging.RabbitMqContracts;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    TopicExchange businessExchange() {
        return new TopicExchange(RabbitMqContracts.BUSINESS_EXCHANGE, true, false);
    }

    @Bean
    Queue seckillOrderCreateQueue() {
        return new Queue(RabbitMqContracts.SECKILL_ORDER_CREATE_QUEUE, true);
    }

    @Bean
    Binding seckillOrderCreateBinding(Queue seckillOrderCreateQueue, TopicExchange businessExchange) {
        return BindingBuilder.bind(seckillOrderCreateQueue)
                .to(businessExchange)
                .with(RabbitMqContracts.SECKILL_ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
