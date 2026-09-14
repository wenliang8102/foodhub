# FoodHub RabbitMQ contracts

This document defines the minimum asynchronous contract between
`foodhub-coupon` and `foodhub-order`. The contract is versioned independently
from either service implementation.

## Topology

| Item | Value |
| --- | --- |
| Exchange | `foodhub.business` |
| Exchange type | `topic` |
| Serialization | JSON, UTF-8 |
| Delivery | persistent message, manual consumer acknowledgement |

The exchange and routing keys are declared in
`foodhub-common-messaging`. Producers and consumers must use those constants
instead of repeating string literals.

| Flow | Routing key | Queue | Producer | Consumer |
| --- | --- | --- | --- | --- |
| Seckill order creation | `coupon.seckill.order.create.v1` | `foodhub.order.seckill-order-create.v1` | `foodhub-coupon` | `foodhub-order` |
| Order cancellation / inventory release | `order.cancelled.v1` | `foodhub.coupon.order-cancelled.v1` | `foodhub-order` | `foodhub-coupon` |

## Seckill order creation

`foodhub-coupon` publishes `SeckillOrderCreateCommand` after the activity has
been checked and Redis inventory has been reserved. `foodhub-order` consumes it
and creates a `PENDING_PAY` order.

```json
{
  "messageId": "msg-01J8...",
  "requestId": "seckill-01J8...",
  "userId": 7,
  "activityId": 1001,
  "targetType": "COUPON",
  "targetId": 2001,
  "quantity": 1,
  "unitPrice": 19.90,
  "occurredAt": "2026-09-14T12:00:00Z",
  "paymentExpiresAt": "2026-09-14T12:15:00Z"
}
```

`targetType` is `FOOD_ITEM` or `COUPON`, and `targetId` identifies the selected
object. `activityId` is always the inventory and idempotency scope.

## Order cancellation

`foodhub-order` publishes `OrderCancelledEvent` after an order transitions to
`CANCELLED` and inventory must be released. `foodhub-coupon` consumes it and
returns the reserved quantity to the activity inventory.

```json
{
  "messageId": "msg-01J9...",
  "orderNo": "FH202609140001",
  "userId": 7,
  "activityId": 1001,
  "targetType": "COUPON",
  "targetId": 2001,
  "quantity": 1,
  "reason": "PAYMENT_TIMEOUT",
  "occurredAt": "2026-09-14T12:15:01Z"
}
```

## Reliability rules

- `messageId` identifies one published message and is required in logs and
  consumer deduplication records.
- `requestId` identifies the original seckill request. The order service must
  not create two orders for the same `requestId` (or the same user/activity
  pair, according to its database constraint).
- Consumers use manual acknowledgement. A message is acknowledged only after
  the local transaction succeeds; transient failures are retried and terminal
  failures go to a dead-letter path when that infrastructure is added.
- Cancellation handling is state-based and idempotent. Re-delivery of an
  already processed cancellation must not restore inventory twice.
- The producer must publish a persistent message and use publisher confirms in
  the implementation phase. The contract itself does not prescribe a specific
  retry or dead-letter exchange.
- Consumers must tolerate unknown JSON fields to allow additive fields in a
  later `v1` patch. Removing or changing the meaning of an existing field
  requires a new routing-key and queue version.

## Java types

The shared module exposes only these integration types:

- `RabbitMqContracts`: exchange, routing-key, and queue names.
- `SeckillOrderCreateCommand`: coupon-to-order command.
- `OrderCancelledEvent`: order-to-coupon inventory release event.

Business entities, database mappers, and service implementations stay in their
own modules.
