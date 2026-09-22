# foodhub-order 订单创建与查询接口

Order 服务通过 RabbitMQ 消费秒杀下单命令，幂等创建 `PENDING_PAY` 订单，并向 Coupon
服务发送订单创建成功事件。待支付订单到达 `paymentExpiresAt` 后会自动取消，并可靠通知
Coupon 服务恢复秒杀库存。

## HTTP 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/orders` | 登录用户 | 查询自己的订单 |
| `GET` | `/api/orders/{orderNo}` | 订单所属用户 | 查询订单详情 |
| `POST` | `/api/orders/{orderNo}/pay` | 订单所属用户 | 模拟支付成功 |

列表参数为 `page` 和 `size`，页码从 1 开始，单页最多 100 条。

模拟支付接口不调用外部支付平台。仅当订单属于当前用户、状态为 `PENDING_PAY` 且尚未超过
`paymentExpiresAt` 时，才会通过数据库条件更新转换为 `PAID` 并记录 `paidAt`。重复支付
已经为 `PAID` 的订单按幂等成功处理；已取消订单返回 `ORDER_CANCELLED`；支付窗口已过期时
返回 `ORDER_PAYMENT_EXPIRED`，并立即执行订单取消和库存补偿流程。

支付更新和超时取消都要求原状态为 `PENDING_PAY`，因此并发发生时只有一个状态转换能够成功。

## 异步流程

```text
Coupon Redis/数据库预留库存
    -> 同事务写 seckill_request 和 seckill_outbox
    -> Outbox publisher 发送 SeckillOrderCreateCommand
    -> publisher confirm 后标记 MESSAGE_SENT
    -> Order 手动 ACK 消费消息
    -> messageId/requestId/用户+活动三层幂等创建 PENDING_PAY 订单
    -> Order 发布 SeckillOrderCreatedEvent
    -> 事件 confirm 后 ACK 原创建命令
    -> Coupon 消费事件并标记 ORDER_CREATED、写入 orderNo
```

Outbox 发送失败时以指数退避方式定时重试。消息可能重复投递，因此 Order 创建和
Coupon 回执处理均按业务键幂等。

## 超时取消

Order 默认每 5 秒扫描一次已过支付截止时间的 `PENDING_PAY` 订单。订单状态更新为
`CANCELLED` 时，会在同一数据库事务写入 `order_outbox`。Outbox 在 RabbitMQ publisher
confirm 成功后标记为 `SENT`，发送失败则按 1 到 60 秒指数退避重试。

Coupon 收到 `OrderCancelledEvent` 后，通过 Redis Lua 删除用户预占标记并恢复缓存库存，
随后在同一数据库事务中恢复活动库存、将秒杀请求更新为 `CANCELLED`，并写入订单号唯一的
补偿记录。重复取消事件不会重复增加库存。
