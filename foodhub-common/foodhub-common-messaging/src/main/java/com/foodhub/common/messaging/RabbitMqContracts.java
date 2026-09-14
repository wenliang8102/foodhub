package com.foodhub.common.messaging;

/** Stable exchange, routing-key, and queue names shared by message producers and consumers. */
public final class RabbitMqContracts {

    public static final String BUSINESS_EXCHANGE = "foodhub.business";

    public static final String SECKILL_ORDER_CREATE_ROUTING_KEY =
            "coupon.seckill.order.create.v1";
    public static final String SECKILL_ORDER_CREATE_QUEUE =
            "foodhub.order.seckill-order-create.v1";

    public static final String ORDER_CANCELLED_ROUTING_KEY =
            "order.cancelled.v1";
    public static final String ORDER_CANCELLED_QUEUE =
            "foodhub.coupon.order-cancelled.v1";

    private RabbitMqContracts() {
    }
}
