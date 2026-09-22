# foodhub-coupon 普通优惠券接口

本阶段实现普通优惠券的创建、上下架、公开查询、领取和用户券查询。秒杀活动及
RabbitMQ 异步下单不在本阶段范围内。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/coupons` | `MERCHANT` / `ADMIN` | 创建并上架优惠券 |
| `PATCH` | `/api/coupons/{id}/status` | 创建者 / `ADMIN` | 设置 `ACTIVE` 或 `INACTIVE` |
| `GET` | `/api/coupons` | 公开 | 查询当前可领取优惠券 |
| `GET` | `/api/coupons/{id}` | 公开 | 查询已上架优惠券详情 |
| `GET` | `/api/coupons/managed` | `MERCHANT` / `ADMIN` | 查询负责管理的优惠券 |
| `POST` | `/api/coupons/{id}/claim` | 登录用户 | 领取优惠券 |
| `GET` | `/api/coupons/mine` | 登录用户 | 查询我的优惠券 |

列表参数统一为 `page`、`size`，页码从 1 开始，单页最多 100 条。公开列表可使用
`merchantId` 筛选商户。

## 创建请求

```json
{
  "merchantId": 101,
  "name": "满 50 减 10",
  "faceValue": 10.00,
  "minSpend": 50.00,
  "totalStock": 1000,
  "validFrom": "2026-09-20T18:00:00+08:00",
  "validUntil": "2026-10-20T23:59:59+08:00"
}
```

`minSpend` 不能小于 `faceValue`，结束时间必须晚于开始时间且处于未来。
创建时 Coupon 服务会通过服务发现查询 Merchant 服务，确认商户存在；`MERCHANT`
只能为自己名下的商户创建优惠券，`ADMIN` 可以为任意有效商户创建。

## 状态与领取规则

- 优惠券展示状态为 `NOT_STARTED`、`AVAILABLE`、`SOLD_OUT`、`EXPIRED` 或 `INACTIVE`。
- 只有 `AVAILABLE` 状态可以领取。
- 同一用户对同一优惠券只能领取一次。
- 库存通过带状态、时间和余量条件的数据库原子更新扣减。
- 用户券当前支持 `UNUSED`，过期后查询时展示为 `EXPIRED`；核销将在订单阶段接入。
