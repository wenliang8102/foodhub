package com.foodhub.coupon.config;

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
    Queue orderCancelledQueue() {
        return new Queue(RabbitMqContracts.ORDER_CANCELLED_QUEUE, true);
    }

    @Bean
    Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange businessExchange) {
        return BindingBuilder.bind(orderCancelledQueue)
                .to(businessExchange)
                .with(RabbitMqContracts.ORDER_CANCELLED_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
