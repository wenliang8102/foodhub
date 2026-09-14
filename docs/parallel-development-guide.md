# 并行开发指南

本文用于固定 `foodhub-merchant` 和 `foodhub-social` 第一阶段的实现边界。
两个服务可以并行开发和合并，但只共享技术规范，不共享业务表、Entity、Mapper 或 Service。

## 公共约定

### 服务归属

| 服务 | 端口 | 数据库 | 负责内容 |
| --- | ---: | --- | --- |
| `foodhub-merchant` | 8102 | `foodhub_merchant` | 商户、分类、菜品 |
| `foodhub-social` | 8103 | `foodhub_social` | 动态、关注、点赞、评论、收藏、Feed |

每个服务遵循以下分层：

```text
Controller -> Service -> Mapper -> Database
```

请求 DTO、持久化 Entity 和响应 VO 分开定义。服务可以保存其他服务的 ID（例如
`merchantId`），但不得引入其他服务的 Entity 或 Mapper。

### 请求和身份契约

- 业务响应统一使用 `foodhub-common-core` 中的 `ApiResponse`。
- 数据库 ID 统一使用 `Long`。
- 时间字段使用带明确偏移量或 `Z` 的 ISO-8601 格式。
- Gateway 校验 Bearer JWT 后，向下游注入可信请求头：`X-User-Id`、`X-Username`、`X-User-Role`。
- 需要当前用户的操作使用 `X-User-Id`，不要信任请求体中的 `userId`。
- 角色使用认证服务已有的大写值，例如 `USER`、`MERCHANT`、`ADMIN`。
- 不得连接 `foodhub_auth`，也不得直接读取其中的 `user` 表。

身份请求头是 Gateway 到业务服务之间的契约。Gateway 会先删除客户端提交的同名请求头，再写入 JWT
解析出的值，防止客户端伪造其他用户。生产流量经过 Gateway；直接访问服务只用于本地开发和测试。

### 文件和模块边界

开发者可以修改自己负责的服务目录并在该目录下添加测试。修改
`foodhub-common-*`、`foodhub-auth`、`foodhub-gateway`、`infrastructure`、根目录 `pom.xml`
前必须与仓库负责人协调。不要在这两个功能分支中重命名公共包，或修改公共响应和安全类型。

本阶段 `merchant` 和 `social` 都不使用 RabbitMQ。RabbitMQ 只用于
[`messaging-contracts.md`](messaging-contracts.md) 中约定的 `coupon` 与 `order` 流程。

## `foodhub-merchant` 模块

### 开发范围

实现用户和后续优惠券、订单所需的商户目录能力：

- 分类管理；
- 商户新增、编辑、列表、详情和营业状态切换；
- 菜品新增、编辑、列表、详情和上下架；
- 关键词和分类筛选；
- 分页，以及按评分或销量进行基础排序；
- 商户和菜品详情的 Redis 缓存。

第一版关键词查询使用 MySQL 即可，不要在此分支加入 Elasticsearch 或新的搜索服务。

### 建议目录

```text
com.foodhub.merchant
  controller
  dto
  entity
  mapper
  service
  vo
```

业务规则放在 Service 中。Controller 负责参数校验、获取 Gateway 身份并返回 VO；Mapper 和持久化
Entity 只留在本模块。

### 数据归属

在 `foodhub-merchant/src/main/resources/db/migration/` 下添加该服务自己的迁移脚本，至少包含：

```text
category
merchant
food_item
```

建议字段和约束：

- 活跃分类名称唯一；
- 商户包含名称、分类 ID、地址、经纬度、评分、销量、营业状态、创建时间和更新时间；
- 菜品包含商户 ID、名称、价格、图片 URL、销量、上下架状态、创建时间和更新时间；
- Service 校验关联 ID 和状态值；
- 软删除或明确的 `status` 字段必须保证不可用数据不会出现在公共列表。

详情缓存使用现有 Redis key helper 生成 `foodhub:merchant:detail:{merchantId}` 和
`foodhub:food:detail:{foodId}`。只缓存公开且有效的详情；商户或管理员更新后必须删除对应缓存。

### API 方向

沿用 Gateway 已配置的路由族：

```text
GET    /api/merchants
GET    /api/merchants/{merchantId}
GET    /api/categories
GET    /api/foods
GET    /api/foods/{foodId}
POST   /api/merchants
PATCH  /api/merchants/{merchantId}
POST   /api/foods
PATCH  /api/foods/{foodId}
```

具体请求和响应字段在实现时补充接口说明。公共读取接口可以匿名访问；写操作需要 `MERCHANT` 或
`ADMIN`。商户角色只能修改自己名下的商户和菜品。不要在此模块实现 coupon、seckill、order 或 social 接口。

### 验收清单

- 未登录用户可以查询有效商户、分类和菜品；
- 管理员或商户写入时会校验参数、角色和数据归属；
- 详情查询在缓存命中时读取 Redis，未命中时查询 MySQL 并回填；
- 下架商户或菜品不会出现在公共结果中；
- 模块不引入 `foodhub-social`、`foodhub-coupon` 或 `foodhub-order` 的类。

## `foodhub-social` 模块

### 开发范围

实现用户美食内容和社交互动：

- 发布、列表、详情和删除美食动态；
- 保存可选的图片 URL 和 `merchantId` 引用；
- 关注和取消关注用户；
- 查询关注列表和粉丝列表；
- 点赞和取消点赞；
- 创建评论、删除自己的评论；
- 收藏和取消收藏；
- 查询关注用户动态的拉模式 Feed。

第一版 Feed 直接按发布时间从数据库查询。后续可以增加 Redis Feed 或推模式，但不改变数据归属。

### 建议目录

```text
com.foodhub.social
  controller
  dto
  entity
  mapper
  service
  vo
```

如果动态规则变复杂，可以在本服务内部增加 `domain` 包，不要为了复用而移动到公共模块。

### 数据归属

在 `foodhub-social/src/main/resources/db/migration/` 下添加该服务自己的迁移脚本，至少包含：

```text
post
user_follow
post_like
post_comment
post_favorite
```

必须实现以下唯一性和可见性规则：

- `(follower_id, following_id)` 唯一，用户不能关注自己；
- `(user_id, post_id)` 在点赞和收藏表中唯一；
- 评论内容不能为空；
- 只有评论作者可以删除评论；
- 只有动态作者可以删除动态；
- 已删除或隐藏的动态不能出现在公共列表和 Feed 中；
- 动态可以保存 `merchantId`，但 Social 不拥有也不关联查询 Merchant 表。

高频点赞计数可以使用 `foodhub:post:like:{postId}`，但必须先建立数据库记录和唯一约束，数据库是防重复的最终依据。

### API 方向

沿用 Gateway 已配置的路由族：

```text
POST   /api/posts
GET    /api/posts
GET    /api/posts/{postId}
DELETE /api/posts/{postId}
POST   /api/follows/{userId}
DELETE /api/follows/{userId}
GET    /api/follows/following
GET    /api/follows/followers
GET    /api/feed
POST   /api/posts/{postId}/likes
DELETE /api/posts/{postId}/likes
POST   /api/posts/{postId}/comments
DELETE /api/posts/{postId}/comments/{commentId}
POST   /api/posts/{postId}/favorite
DELETE /api/posts/{postId}/favorite
```

发帖、关注、点赞、评论、收藏和删除都需要登录。公共动态列表和详情必须过滤隐藏内容。
所有写操作从 `X-User-Id` 获取操作者，不得信任客户端提交的 `userId`。

第一版 Social 不同步调用 Merchant，只接收和返回 `merchantId` 引用。如果以后需要商户展示信息，
应有意设计一个小型 HTTP 查询契约或本地快照，不得引入 Merchant 的持久化类。

### 验收清单

- 登录用户可以创建并删除自己的动态；
- 用户不能关注自己，关注、点赞和收藏记录不能重复；
- 用户可以发表评论，且只能删除自己的评论；
- Feed 只包含关注用户发布的可见动态；
- 模块不引入 `foodhub-merchant`、`foodhub-coupon` 或 `foodhub-order` 的类。

## 合并和交接流程

1. 使用独立分支，例如 `feature/merchant-v0.2` 和 `feature/social-v0.2`。
2. 提交保持聚焦，只包含自己负责的服务；不要提交 `target/` 和本地配置。
3. 交接前从仓库根目录执行对应模块的聚焦构建：

   ```powershell
   .\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-merchant -am test
   .\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
   ```

4. 交接说明中列出迁移脚本、接口路径、所需角色、Redis key，以及对其他服务的假设。
   仓库负责人负责合并前的根构建和 Gateway 冒烟检查。

两个模块与后续业务的第一个集成点是 ID 契约：coupon 和 order 可以引用 `merchantId`、`foodItemId`
或活动目标，但不得直接访问这两个服务的数据库。
