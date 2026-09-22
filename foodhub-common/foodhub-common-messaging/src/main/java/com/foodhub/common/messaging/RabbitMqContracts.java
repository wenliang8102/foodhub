package com.foodhub.common.messaging;

/** Stable exchange, routing-key, and queue names shared by message producers and consumers. */
public final class RabbitMqContracts {

    public static final String BUSINESS_EXCHANGE = "foodhub.business";
    public static final String DEAD_LETTER_EXCHANGE = "foodhub.dead-letter";
    public static final String DEAD_LETTER_QUEUE = "foodhub.dead-letter.v1";
    public static final String RETRY_COUNT_HEADER = "x-foodhub-retry-count";
    public static final int MAX_CONSUMER_RETRIES = 3;

    public static final String SECKILL_ORDER_CREATE_ROUTING_KEY =
            "coupon.seckill.order.create.v1";
    public static final String SECKILL_ORDER_CREATE_QUEUE =
            "foodhub.order.seckill-order-create.v1";

    public static final String SECKILL_ORDER_CREATED_ROUTING_KEY =
            "order.seckill.created.v1";
    public static final String SECKILL_ORDER_CREATED_QUEUE =
            "foodhub.coupon.seckill-order-created.v1";

    public static final String ORDER_CANCELLED_ROUTING_KEY =
            "order.cancelled.v1";
    public static final String ORDER_CANCELLED_QUEUE =
            "foodhub.coupon.order-cancelled.v1";

    private RabbitMqContracts() {
    }
}
