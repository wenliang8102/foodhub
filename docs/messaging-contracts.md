# FoodHub RabbitMQ 消息契约

本文定义 `foodhub-coupon` 和 `foodhub-order` 之间的最小异步通信契约。
契约版本独立于两个服务的具体实现进行管理。

## 拓扑

| 项目 | 值 |
| --- | --- |
| Exchange | `foodhub.business` |
| 死信 Exchange | `foodhub.dead-letter` |
| 死信 Queue | `foodhub.dead-letter.v1` |
| Exchange 类型 | `topic` |
| 序列化 | JSON、UTF-8 |
| 投递方式 | 持久化消息，消费者手动确认 |

Exchange、路由键和队列名称统一声明在 `foodhub-common-messaging` 中。
生产者和消费者必须使用这些常量，不要在业务代码中重复写字符串。

两个业务服务启动后，Spring AMQP 会根据各自的配置自动声明共享 Exchange、
本服务负责消费的 Queue 和 Binding。RabbitMQ 容器单独启动时不会预先出现这些业务对象。

| 流程 | Routing key | Queue | 生产者 | 消费者 |
| --- | --- | --- | --- | --- |
| 创建秒杀订单 | `coupon.seckill.order.create.v1` | `foodhub.order.seckill-order-create.v1` | `foodhub-coupon` | `foodhub-order` |
| 秒杀订单创建成功 | `order.seckill.created.v1` | `foodhub.coupon.seckill-order-created.v1` | `foodhub-order` | `foodhub-coupon` |
| 订单取消 / 库存释放 | `order.cancelled.v1` | `foodhub.coupon.order-cancelled.v1` | `foodhub-order` | `foodhub-coupon` |

## 创建秒杀订单

`foodhub-coupon` 完成活动校验并在 Redis 中预扣库存后，发布
`SeckillOrderCreateCommand`。`foodhub-order` 消费该消息并创建一个 `PENDING_PAY` 订单。

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

`targetType` 的取值为 `FOOD_ITEM` 或 `COUPON`，`targetId` 表示被秒杀对象。
`activityId` 始终是库存和幂等控制的业务范围。

## 秒杀订单创建成功

`foodhub-order` 幂等创建订单后发布 `SeckillOrderCreatedEvent`。Coupon 服务据此将
秒杀请求推进到 `ORDER_CREATED` 并记录 `orderNo`。原创建命令只有在该事件获得
publisher confirm 后才会 ACK，因此临时发布失败会通过原命令重投恢复。

## 订单取消

当订单转换为 `CANCELLED` 且需要释放库存时，`foodhub-order` 发布
`OrderCancelledEvent`。`foodhub-coupon` 消费该事件，并将预扣数量返还到活动库存。

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

## 可靠性规则

- `messageId` 标识一条已发布的消息，必须写入日志和消费者去重记录。
- `requestId` 标识原始秒杀请求。订单服务不得因同一个 `requestId` 重复创建订单；
  也可以通过用户与活动组合的数据库约束进行兜底。
- 消费者使用手动确认。只有本地事务成功后才能确认消息；临时故障需要重试，
  消费失败最多重新发布 3 次，之后进入 `foodhub.dead-letter.v1`。只有重试或死信消息
  获得 publisher confirm 后才确认原消息，避免转发期间丢失。
- 取消处理必须基于状态并具备幂等性。重复投递已经处理过的取消事件时，不得重复恢复库存。
- 实现阶段生产者必须发布持久化消息并启用 publisher confirms。契约本身不限制具体的重试或死信交换机方案。
- 消费者必须忽略未知 JSON 字段，以支持后续在 `v1` 中追加字段。删除已有字段或改变字段含义时，
  必须升级 routing key 和 queue 版本。

## Java 类型

公共模块只暴露以下集成类型：

- `RabbitMqContracts`：Exchange、routing key 和 queue 名称。
- `SeckillOrderCreateCommand`：从 coupon 发往 order 的创建订单命令。
- `SeckillOrderCreatedEvent`：从 order 发往 coupon 的订单创建成功事件。
- `OrderCancelledEvent`：从 order 发往 coupon 的库存释放事件。

业务 Entity、数据库 Mapper 和服务实现仍保留在各自模块中。
