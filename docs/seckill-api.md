# foodhub-coupon 秒杀准入接口

本阶段实现秒杀活动管理、Redis 库存预热、动态路径、原子库存预留、防重复参与和
结果查询。RabbitMQ 异步创建订单将在下一阶段接入，因此当前成功结果为
`STOCK_RESERVED`。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/seckill/activities` | `MERCHANT` / `ADMIN` | 创建秒杀活动 |
| `GET` | `/api/seckill/activities` | 公开 | 查询有效活动 |
| `GET` | `/api/seckill/activities/{id}` | 公开 | 查询活动详情 |
| `POST` | `/api/seckill/activities/{id}/preheat` | 创建者 / `ADMIN` | 活动开始前预热 Redis 库存 |
| `POST` | `/api/seckill/activities/{id}/path` | 登录用户 | 获取 5 分钟动态秒杀路径 |
| `POST` | `/api/seckill/activities/{id}/submit` | 登录用户 | 提交秒杀请求 |
| `GET` | `/api/seckill/activities/{id}/result` | 登录用户 | 查询自己的秒杀结果 |

## 创建活动

```json
{
  "merchantId": 101,
  "targetType": "FOOD_ITEM",
  "targetId": 1001,
  "title": "招牌辣子鸡限时秒杀",
  "seckillPrice": 19.90,
  "totalStock": 100,
  "startAt": "2026-09-21T10:00:00+08:00",
  "endAt": "2026-09-21T10:10:00+08:00"
}
```

`targetType` 支持 `FOOD_ITEM` 和 `COUPON`。系统会确认目标属于指定商户，商户用户
还必须是该商户的所有者。

## 准入流程

```text
活动开始前预热库存
    -> 登录用户获取动态 path
    -> 提交 path
    -> Lua 校验 path、防重复并原子扣减 Redis 库存
    -> 数据库 version 乐观扣减库存并记录请求
    -> 返回 STOCK_RESERVED
```

如果数据库扣减或请求记录失败，系统使用补偿 Lua 归还 Redis 库存并移除用户占位。
同一用户和活动在数据库还有唯一索引兜底。

Lua 脚本在 Redis 内串行、原子执行库存和用户占位操作，因此这里不再额外持有一把
Redisson 锁；它承担了同一临界区的分布式互斥职责，同时避免锁超时导致的并发空窗。
