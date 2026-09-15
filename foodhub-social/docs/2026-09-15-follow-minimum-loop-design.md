# Social 关注最小闭环设计

## 实现范围

本次在已有帖子最小闭环基础上，仅实现以下关注能力：

- 关注用户；
- 取消关注用户；
- 分页查询当前用户的关注列表；
- 分页查询当前用户的粉丝列表。

本次不实现 Feed、点赞、评论、收藏或用户资料聚合。所有新增和修改均限制在
`foodhub-social`，不得修改或引入 `foodhub-merchant`、`foodhub-auth`、
`foodhub-coupon`、`foodhub-order` 的业务类，也不修改公共模块、网关、基础设施
或根 Maven 配置。

## 系统架构

实现遵循项目规定的分层结构：

```text
FollowController -> FollowService -> FollowMapper -> foodhub_social.user_follow
```

- `FollowController`：定义 `/api/follows` 接口，解析 `X-User-Id`、路径参数和
  分页参数，并使用 `ApiResponse` 包装响应；
- `FollowService`：校验用户标识和自关注规则，处理幂等关注、幂等取消以及列表
  查询；
- `FollowMapper`：执行幂等插入、删除、总数统计和分页查询；
- `FollowEntity`：只承载 Social 数据库中的关注记录；
- `FollowView` 和 `FollowPageView`：定义列表响应，不直接暴露持久化实体。

Social 只保存用户 ID，不读取 `foodhub_auth.user`，也不直接调用 Auth 服务验证
目标用户是否存在。

## 数据模型

新增迁移文件：

```text
foodhub-social/src/main/resources/db/migration/V2__create_user_follow_table.sql
```

迁移创建 `user_follow` 表：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT` | 自增主键 |
| `follower_id` | `BIGINT` | 发起关注的用户 ID，必须为正数 |
| `following_id` | `BIGINT` | 被关注用户 ID，必须为正数 |
| `created_at` | `DATETIME` | 关注建立时间，不能为空 |

数据库约束和索引：

- `(follower_id, following_id)` 唯一，作为并发情况下的最终防重保障；
- `follower_id != following_id`，从数据库层阻止自关注；
- 为关注列表建立 `(follower_id, created_at, id)` 索引；
- 为粉丝列表建立 `(following_id, created_at, id)` 索引。

不修改已有 `V1` 迁移和 `post` 表。

## HTTP 接口

### 关注用户

`POST /api/follows/{userId}`

必须提供 `X-User-Id`。请求路径中的 `userId` 是被关注用户。两个用户 ID 都只
接受正整数，当前用户不能关注自己。

关注操作保持幂等：首次请求创建记录，重复请求不新增记录并继续返回成功。

### 取消关注

`DELETE /api/follows/{userId}`

必须提供 `X-User-Id`。存在对应关注记录时删除记录；记录不存在时也返回成功，
便于客户端安全重试。

### 查询关注列表

`GET /api/follows/following?page=1&pageSize=20`

必须提供 `X-User-Id`。查询当前用户关注的人，按照
`created_at DESC, id DESC` 排序。

### 查询粉丝列表

`GET /api/follows/followers?page=1&pageSize=20`

必须提供 `X-User-Id`。查询关注当前用户的人，按照
`created_at DESC, id DESC` 排序。

两个列表的 `page` 从 1 开始，`pageSize` 默认为 20，允许范围为 1 到 100。

## 响应模型

关注和取消关注成功时返回统一的空数据成功响应。

列表使用 `FollowPageView`，包含：

- `page`：当前页码；
- `pageSize`：每页条数；
- `total`：符合条件的关注关系总数；
- `items`：当前页的关注关系。

每个 `FollowView` 只包含：

- `userId`：关注列表中表示被关注用户，粉丝列表中表示粉丝用户；
- `followedAt`：关注关系建立时间。

`followedAt` 使用带明确 `+08:00` 偏移量的 ISO-8601 格式，与 Social 数据库的
`Asia/Shanghai` 时区保持一致。第一版不返回用户名、头像或其他 Auth 用户资料。

## 幂等和并发处理

关注写入采用数据库唯一约束配合 MySQL `INSERT IGNORE`：

1. 首次关注插入一条记录；
2. 相同用户对的重复或并发请求命中唯一约束时不新增记录；
3. Mapper 返回影响行数为 0 时，Service 将其视为已经关注并返回成功。

取消关注直接按 `(follower_id, following_id)` 删除。删除影响行数为 0 时表示此前
已经取消，仍返回成功。

Service 在写入数据库前校验自关注，数据库检查约束作为最终保护。数据库异常不
被错误地转换为幂等成功；只有相同用户对的唯一键冲突由 `INSERT IGNORE` 消除。

## 错误处理

业务错误继续使用公共的 `BusinessException` 和 `GlobalExceptionHandler`：

| 错误码 | 含义 |
| --- | --- |
| `INVALID_USER_ID` | `X-User-Id` 或路径中的用户 ID 缺失、格式错误或不是正数 |
| `FOLLOW_SELF_NOT_ALLOWED` | 当前用户尝试关注自己 |
| `VALIDATION_ERROR` | `page` 或 `pageSize` 超出允许范围 |

由于 Social 不读取 Auth 数据，目标用户 ID 为正数但实际不存在时，本模块不会
返回“用户不存在”错误。目标用户存在性由未来经过评审的跨服务查询契约解决，
不在本次范围内。

## 测试策略

开发过程采用测试驱动方式，先编写失败测试，再添加最小实现。

测试覆盖：

- 迁移脚本包含表结构、唯一约束、自关注约束和两个分页索引；
- Mapper 包含幂等插入、按用户对删除、双向计数和稳定排序分页 SQL；
- Service 覆盖首次关注、重复关注、自关注、首次取消、重复取消、关注列表、粉丝
  列表和非法分页；
- Controller 覆盖四个接口、身份请求头解析、非法路径用户 ID、分页参数和统一
  响应结构；
- 并发防重由数据库唯一约束和 Mapper SQL 契约共同保证。

交付前执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

同时使用 Git 变更检查确认所有文件均位于 `foodhub-social`，并搜索 Java 导入，
确认没有依赖 Merchant、Auth、Coupon 或 Order 的业务类型。

## 验收标准

- 登录用户可以关注其他正数用户 ID，不能关注自己；
- 重复或并发关注最多保留一条记录，并返回成功；
- 取消不存在的关注关系也返回成功；
- 当前用户可以分页查询关注列表和粉丝列表；
- 两个列表均按关注建立时间和记录 ID 倒序稳定排列；
- 响应不包含其他服务拥有的用户资料；
- 完整 Social 聚焦测试通过；
- `foodhub-merchant` 和其他边界外模块没有任何变更。
