# FoodHub 架构说明

## 仓库边界

FoodHub 使用单仓库管理多个可独立运行的服务。每个业务服务拥有自己的数据，
并遵循常见的 Java 分层流程：

```text
Controller -> Service -> Mapper -> Database
```

请求 DTO、持久化 Entity 和响应 VO 分开定义。服务之间不得直接引入其他服务的
Entity 或 Mapper。跨服务同步调用使用 HTTP 契约，需要异步处理的场景使用 RabbitMQ 事件。

## 模块划分

| 模块 | 端口 | 职责 |
| --- | ---: | --- |
| `foodhub-gateway` | 8080 | 外部路由和边缘层处理 |
| `foodhub-auth` | 8101 | 账号、认证和角色权限 |
| `foodhub-merchant` | 8102 | 商户、分类和菜品 |
| `foodhub-social` | 8103 | 动态、关注、点赞、评论和 Feed |
| `foodhub-coupon` | 8104 | 优惠券和秒杀活动 |
| `foodhub-order` | 8105 | 订单、支付状态和取消流程 |
| `foodhub-common-*` | 不适用 | 可复用的技术基础模块，包括消息契约 |

公共模块可以存放技术规范和工具，但不得存放共享业务 Entity 或共享持久化模型。

## 运行模型

MySQL、Redis、RabbitMQ 和 Nacos 在 Docker 中运行。应用服务可以从 IDE 或 Maven
Wrapper 启动，便于快速调试。每个服务拥有独立的逻辑 MySQL 数据库，本地开发时共用同一个 MySQL 实例。

配置通过环境变量支持本地覆盖。第一阶段使用 Nacos 提供服务发现；当出现第一批
需要共享的运行参数后，再引入集中式配置。
