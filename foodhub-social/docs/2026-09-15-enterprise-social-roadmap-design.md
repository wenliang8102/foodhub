# Social 企业级演进路线设计

## 文档目标

本文定义 `foodhub-social` 在已有动态功能基础上的完整演进路线。目标不是继续
交付孤立的小功能，而是在保持仓库边界和接口兼容的前提下，逐步形成具备完整
社交能力、数据一致性、缓存降级、内容治理、可观测性和自动化验证的 Social
业务模块。

本文取代以下两份范围过窄的文档：

- `2026-09-15-follow-minimum-loop-design.md`；
- `2026-09-15-follow-minimum-loop-implementation-plan.md`。

关注关系仍然是完整 Social 模块的基础能力，但不再作为最终交付目标。后续每个
阶段必须分别编写中文设计和中文实施计划，并通过独立 Pull Request 交付。

## 当前基线

阶段 0 已完成以下动态能力：

- 发布动态；
- 分页查询公开动态；
- 查询动态详情；
- 作者软删除自己的动态；
- 保存图片 URL 和可选的 `merchantId` 数值引用；
- 使用 `X-User-Id` 获取当前操作用户；
- 使用 Flyway 管理 `post` 表；
- 覆盖 Controller、Service、Mapper 和迁移契约测试。

阶段 0 对应上游 Pull Request：

```text
https://github.com/wenliang8102/foodhub/pull/1
```

后续阶段必须保留上述接口兼容性，不重命名或删除现有响应字段。

## 范围与边界

### 允许范围

企业级 Social 最终包含：

- 动态发布、列表、详情和作者删除；
- 关注、取消关注、关注列表和粉丝列表；
- 点赞、取消点赞及点赞状态；
- 收藏、取消收藏及收藏列表；
- 评论创建、分页查询和作者删除；
- 关注用户动态的拉模式 Feed；
- 管理员隐藏和恢复动态；
- 点赞数、评论数和收藏数一致性维护；
- Redis 热点缓存与数据库降级；
- 参数防御、权限控制、结构化日志、指标和中文接口文档；
- 单元测试、Web 测试、SQL 契约测试和 MySQL 集成测试。

### 禁止范围

所有业务代码和文档变更必须位于 `foodhub-social`。不得修改或引入以下模块的
业务 Entity、Mapper 或 Service：

```text
foodhub-merchant
foodhub-auth
foodhub-coupon
foodhub-order
```

未经仓库负责人协调，不修改：

```text
foodhub-common-*
foodhub-gateway
infrastructure
根目录 pom.xml
```

Social 不连接 `foodhub_auth`，不读取其中的用户表，也不关联查询 Merchant
数据库。`merchantId` 和用户 ID 始终只是跨服务标识引用。

本路线不增加私信、通知中心、用户资料聚合、举报中心、推荐算法、搜索服务或
RabbitMQ 流程。生产入口限流和 JWT 校验继续由 Gateway 负责。

## 设计原则

1. MySQL 是权威数据源，Redis 中的数据必须可以从数据库重建。
2. 数据正确性优先于缓存命中率和提前优化。
3. 唯一约束和事务负责并发正确性，不能只依赖应用层先查后写。
4. 所有写操作只信任 Gateway 注入的 `X-User-Id`。
5. 管理操作额外校验 Gateway 注入的 `X-User-Role=ADMIN`。
6. API 使用 `ApiResponse`，数据库 ID 使用 `Long`，时间使用带偏移量的
   ISO-8601 格式。
7. Flyway 迁移只向前新增，不修改已经执行的迁移文件。
8. 接口和表结构按阶段向后兼容演进，避免一次性大改。
9. 每个阶段都有独立设计、实施计划、测试、提交和 Pull Request。
10. 每阶段交付前必须确认 `foodhub-merchant` 和其他边界外模块没有变更。

## 总体架构

Social 保持单服务部署，并遵循仓库规定的分层：

```text
HTTP 请求
  -> Controller
  -> Service / 事务与业务规则
  -> Mapper
  -> foodhub_social MySQL
             |
             -> Redis Cache-Aside（仅加速）
```

主要组件如下：

- `PostController/PostService/PostMapper`：现有动态能力、可见性和统计字段；
- `FollowController/FollowService/FollowMapper`：用户关注关系；
- `InteractionController/InteractionService`：点赞和收藏；
- `CommentController/CommentService/CommentMapper`：评论；
- `ModerationController/ModerationService`：管理员内容治理；
- `FeedController/FeedService/FeedMapper`：关注 Feed；
- Social 内部身份解析组件：统一解析用户 ID 和角色请求头；
- Social 专用异常处理器：映射稳定业务错误码和 HTTP 状态；
- Social 本地 Redis Key 组件：避免修改公共 Redis Key 工具；
- 指标和日志组件：记录结果、耗时和缓存状态，不记录正文或凭据。

请求 DTO、持久化 Entity 和响应 VO 必须分离。Controller 只处理 HTTP 契约，
Service 负责业务规则和事务，Mapper 只访问 `foodhub_social` 自有表。

## 最终接口

### 动态

```text
POST   /api/posts
GET    /api/posts
GET    /api/posts/{postId}
DELETE /api/posts/{postId}
```

保留现有行为。动态响应逐步增加 `likeCount`、`commentCount`、
`favoriteCount`。请求经过登录 Gateway 时，还可以返回当前用户的 `liked` 和
`favorited` 状态；匿名请求中这两个字段允许为空。

### 关注

```text
POST   /api/follows/{userId}
DELETE /api/follows/{userId}
GET    /api/follows/following?page=1&pageSize=20
GET    /api/follows/followers?page=1&pageSize=20
```

关注和取消关注保持幂等。列表只返回关系另一端的用户 ID 和关注建立时间，不
聚合 Auth 用户资料。

### 点赞与收藏

```text
POST   /api/posts/{postId}/likes
DELETE /api/posts/{postId}/likes
POST   /api/posts/{postId}/favorite
DELETE /api/posts/{postId}/favorite
GET    /api/posts/favorites?page=1&pageSize=20
```

点赞、收藏及其取消操作均保持幂等。收藏列表只返回仍然可见的动态。

### 评论

```text
POST   /api/posts/{postId}/comments
GET    /api/posts/{postId}/comments?page=1&pageSize=20
DELETE /api/posts/{postId}/comments/{commentId}
```

评论内容去除首尾空格后必须为 1 至 500 个字符。只有评论作者可以删除评论。公开
评论列表不返回已删除评论，也不能读取隐藏或删除动态下的评论。

### 内容治理

```text
PATCH /api/posts/{postId}/visibility
```

请求只允许 `ADMIN` 将动态在 `VISIBLE` 和 `HIDDEN` 之间切换，并要求填写去除
首尾空格后 1 至 500 个字符的治理原因。已经由作者删除的动态不能恢复。

### Feed

```text
GET /api/feed?limit=20&beforePublishedAt=...&beforePostId=...
```

Feed 只返回当前用户所关注用户发布的可见动态。第一页不传游标；后续页同时传
`beforePublishedAt` 和 `beforePostId`，使用
`published_at DESC, id DESC` 的键集分页，避免深分页性能退化。

## 数据模型

### `post`

保留现有动态主体，并通过新增迁移逐步增加：

| 字段 | 作用 |
| --- | --- |
| `like_count` | 点赞数，非负 |
| `comment_count` | 可见评论数，非负 |
| `favorite_count` | 收藏数，非负 |
| `hidden_at` | 最近隐藏时间 |
| `hidden_by` | 执行隐藏的管理员 ID |
| `hidden_reason` | 最近一次隐藏原因 |

`status` 最终允许 `VISIBLE`、`HIDDEN` 和 `DELETED`。现有 V1 中的状态检查约束
通过新迁移删除并重新创建，不直接修改 V1。

### `user_follow`

| 字段 | 作用 |
| --- | --- |
| `id` | 自增主键 |
| `follower_id` | 发起关注的用户 ID |
| `following_id` | 被关注用户 ID |
| `created_at` | 关注建立时间 |

约束：两个用户 ID 为正数、不能相同，且
`(follower_id, following_id)` 唯一。分别建立关注列表和粉丝列表的稳定分页
索引。用户 ID 不设置跨 Auth 数据库外键。

### `post_like`

保存 `id`、`user_id`、`post_id` 和 `created_at`。
`(user_id, post_id)` 唯一，并为帖子计数和用户状态查询建立索引。

### `post_favorite`

保存 `id`、`user_id`、`post_id` 和 `created_at`。
`(user_id, post_id)` 唯一，并为收藏列表建立
`(user_id, created_at, id)` 索引。

### `post_comment`

保存 `id`、`post_id`、`author_id`、`content`、`status`、`created_at`、
`updated_at` 和 `deleted_at`。评论状态至少包含 `VISIBLE` 和 `DELETED`，评论
分页使用 `(post_id, status, created_at, id)` 索引。

### `post_moderation_log`

追加保存 `id`、`post_id`、`operator_id`、`from_status`、`to_status`、
`reason` 和 `created_at`。治理日志只追加，不在恢复动态时覆盖历史记录。

Social 内部的点赞、收藏、评论和治理表可以引用 `post.id`。由于动态采用软删除，
这些关联数据不因动态状态变化而物理删除。

## 事务与一致性

### 关注、点赞和收藏

- 数据库唯一约束是最终防重保障；
- 首次新增关系时返回成功；
- 重复新增不创建第二条记录，也返回成功；
- 取消存在关系时删除记录；
- 取消不存在关系时继续返回成功；
- 自关注返回 `FOLLOW_SELF_NOT_ALLOWED`。

### 互动计数

点赞、收藏或评论的关系变更与对应 `post` 计数更新位于同一事务：

1. 先尝试新增或删除事实记录；
2. 只有影响行数为 1 时才更新计数；
3. 计数使用 SQL 原子增减；
4. 递减条件确保结果不小于 0；
5. 事务提交后再处理缓存失效；
6. 缓存失败不能回滚已成功提交的数据库事务。

后续提供可重复执行的计数校验与修复任务，以关系表和可见评论表重新计算计数，
用于修复人工改库或历史异常。

### 删除与隐藏

- 动态和评论使用软删除；
- 关注、点赞和收藏关系在取消时物理删除；
- 隐藏动态保留互动与评论数据，但所有公共读取和互动写入均把它视为不可见；
- 作者删除动态后不能通过治理接口恢复；
- 评论作者重复删除自己的已删除评论返回成功；
- 其他用户不能通过重复删除探测或改变评论归属；
- 动态重复删除保持阶段 0 的 `POST_NOT_FOUND` 行为，保证兼容。

## 身份与权限

- Gateway 负责验证 Bearer JWT，并删除客户端伪造的身份请求头；
- Social 只读取 Gateway 注入的 `X-User-Id`、`X-Username` 和
  `X-User-Role`；
- 所有写操作、收藏列表和 Feed 必须有合法的正数 `X-User-Id`；
- 管理接口要求角色严格等于大写 `ADMIN`；
- 公共动态列表、动态详情和评论列表允许匿名访问；
- 公共接口若收到可选 `X-User-Id`，存在时必须是合法正数；
- Social 不自行解析 JWT，不读取 Auth 数据，也不验证目标用户是否真实存在；
- 未登录访问受保护接口的生产拦截由 Gateway 完成，Social 仍保留身份头防御校验，
  便于直接访问测试和防止错误路由。

## 错误契约

Social 增加模块内异常处理器，继续复用公共 `ApiResponse`，但不修改公共异常处理
类型。Social 应用切换到本地异常处理后，业务错误映射如下：

| HTTP 状态 | 示例错误码 | 含义 |
| ---: | --- | --- |
| 400 | `INVALID_USER_ID`、`VALIDATION_ERROR` | 身份或参数格式错误 |
| 403 | `POST_FORBIDDEN`、`COMMENT_FORBIDDEN`、`MODERATION_FORBIDDEN` | 权限不足 |
| 404 | `POST_NOT_FOUND`、`COMMENT_NOT_FOUND` | 资源不存在或不可见 |
| 500 | `INTERNAL_ERROR` | 未预期异常 |

`FOLLOW_SELF_NOT_ALLOWED` 使用 HTTP 400。幂等关系操作不使用 409。隐藏和已删除
动态对普通读取统一返回 `POST_NOT_FOUND`，避免暴露治理状态。

异常响应不得包含 SQL、堆栈、数据库地址、令牌或内部类名。日志可以记录错误码、
请求 ID 和资源 ID，但不能记录帖子正文、评论正文或认证凭据。

## 分页与查询

- 现有动态列表、关注列表、粉丝列表、收藏列表和评论列表继续使用
  `page/pageSize`；
- `page` 从 1 开始，`pageSize` 默认 20，最大 100；
- 普通列表使用明确的总数 SQL 和稳定的次级 ID 排序；
- Feed 默认 `limit=20`，最大 50；
- Feed 游标的时间和帖子 ID 必须同时出现或同时省略；
- Feed 按 `published_at DESC, id DESC` 查询，下一页使用严格小于游标的组合条件；
- 所有公开动态查询都显式包含 `status='VISIBLE'`；
- Mapper SQL 必须有契约测试验证过滤条件、排序、限制和索引对应关系。

## Redis 缓存

Redis 在最后阶段作为可选加速层引入，不改变前五个阶段的数据库正确性。
Social 在本模块内定义 Key，不修改公共 `RedisKeys`：

```text
foodhub:post:detail:{postId}
foodhub:post:like:{postId}
foodhub:post:favorite:{postId}
foodhub:post:comment:{postId}
foodhub:post:null:{postId}
```

策略如下：

- 使用 Cache-Aside；
- 动态基础详情和各计数分开缓存，避免一次互动使整个详情缓存持续失效；
- 动态基础详情默认缓存 5 分钟，并加入 0 至 60 秒随机抖动；
- 点赞、收藏和评论计数默认缓存 1 分钟，并加入 0 至 15 秒随机抖动；
- 不存在或不可见动态写入空值缓存 30 秒，降低重复穿透；
- 动态删除、隐藏、恢复和内容变化后删除详情及空值缓存；
- 点赞、收藏和评论事务提交后删除或更新对应计数缓存；
- Redis 超时或不可用时直接查询 MySQL，并记录降级指标；
- 不在本阶段缓存 Feed，避免引入推模式和复杂一致性；
- 缓存中的任何值都不能作为权限和数据存在性的唯一依据。

缓存值使用 JSON 序列化。上述 TTL 均通过 Social 配置项提供默认值，阶段 6 的
实现设计负责固定配置键和 Redis 超时。

## 可观测性与日志

继续使用现有 Actuator，并在 Social 内增加业务指标：

- 关注、点赞、收藏和评论写入结果计数；
- Feed 查询耗时和返回条数；
- 动态详情缓存命中、未命中、空值命中和 Redis 降级次数；
- 内容隐藏和恢复次数；
- 计数校验发现及修复的不一致数量。

请求日志使用请求 ID 串联，并至少记录接口、HTTP 方法、结果状态、业务错误码和
耗时。客户端请求 ID 最长 64 个字符，只允许字母、数字、点、下划线和连字符；
缺失或不合法时由 Social 生成 UUID。日志不
输出动态正文、评论正文、JWT、密码、Redis 值或数据库连接信息。

## 测试策略

### 单元测试

- Service 覆盖成功、幂等、权限、不可见资源和事务分支；
- 对数据库影响行数为 0 和 1 的情况分别验证；
- 验证关注与互动不能产生重复关系；
- 验证计数只在事实记录发生变化时更新；
- 验证 Feed 游标边界和响应转换；
- 验证 Redis 命中、未命中、失效和异常回退。

### Web 测试

- MockMvc 覆盖所有接口路径、请求头、角色、参数和响应结构；
- 验证匿名与登录请求的差异；
- 验证 400、403、404 和 500 响应不泄露内部信息；
- 保留阶段 0 动态接口回归测试。

### 持久化测试

- Mapper 契约测试检查 SQL 中的可见性、排序、分页和原子计数条件；
- Flyway 契约测试检查迁移文件、唯一约束、检查约束和索引；
- Testcontainers MySQL 测试真实执行全部迁移；
- 集成测试验证唯一冲突、事务回滚、软删除过滤、Feed 查询和计数一致性；
- Redis 测试验证 Key、TTL、缓存序列化和数据库降级。

### 每阶段验证

每阶段从仓库根目录执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
git diff --check
git diff --name-only main...HEAD
rg -n "com\.foodhub\.(merchant|auth|coupon|order)" foodhub-social/src
```

验收要求：Maven 构建成功、没有失败测试、没有空白错误、所有变更都位于 Social，
并且 Java 源码没有导入其他业务服务类型。

## 分阶段路线

### 阶段 0：动态基础能力

状态：已完成并提交 Pull Request #1。

保留创建、列表、详情和作者删除功能，作为后续所有阶段的基线。该 Pull Request
不追加后续功能，避免扩大评审范围。

### 阶段 1：社交关系与模块基础规范

交付内容：

- 统一 Social 身份请求头解析；
- 建立 Social 专用异常映射并回归现有动态接口；
- 创建 `user_follow`；
- 实现关注、取消关注、关注列表和粉丝列表；
- 验证幂等、并发唯一性、禁止自关注和稳定分页。

本阶段为后续 Feed 提供关系数据，但不实现 Feed。

### 阶段 2：点赞与收藏

交付内容：

- 创建 `post_like` 和 `post_favorite`；
- 为 `post` 增加点赞数和收藏数；
- 实现点赞、收藏及取消接口；
- 实现收藏列表；
- 动态响应增加计数和当前用户状态；
- 验证事实记录与计数在同一事务中一致变化。

### 阶段 3：评论体系

交付内容：

- 创建 `post_comment`；
- 为 `post` 增加评论数；
- 实现评论创建、分页查询和作者删除；
- 验证内容长度、权限、软删除和评论计数一致性；
- 删除动态不允许新增及读取评论；阶段 4 启用隐藏状态后复用相同不可见规则。

### 阶段 4：内容治理

交付内容：

- 扩展动态状态为 `VISIBLE/HIDDEN/DELETED`；
- 增加治理字段和 `post_moderation_log`；
- 实现管理员隐藏和恢复接口；
- 统一所有读取及互动操作的可见性规则；
- 验证角色权限、状态机和审计记录。

### 阶段 5：关注 Feed

交付内容：

- 实现数据库拉模式 Feed；
- 只查询关注用户发布的可见动态；
- 使用时间和帖子 ID 组合游标；
- 补充 Feed 所需组合索引；
- 验证同一时间动态、空 Feed、边界游标和隐藏内容过滤。

### 阶段 6：性能与工程治理

交付内容：

- 引入动态详情和计数 Redis Cache-Aside；
- 实现缓存失效、空值缓存和数据库降级；
- 增加计数校验与修复任务；
- 增加 Testcontainers MySQL 与 Redis 集成测试；
- 增加指标、请求 ID 和结构化日志；
- 完成中文 API、数据库迁移、缓存和运维文档；
- 执行完整 Social 回归与故障场景测试。

阶段 6 完成后，才把 Social 模块整体标记为本路线定义的企业级交付状态。

## 交付与分支策略

- Pull Request #1 保持阶段 0 范围不变；
- 后续阶段在前一阶段合并后，从最新 `main` 创建新的 `codex/social-*` 分支；
- 每个阶段只包含本阶段的迁移、代码、测试和文档；
- 一个阶段未通过完整 Social 回归时，不开始下一阶段的业务实现；
- 新增响应字段必须提供兼容默认值，不删除或重命名现有字段；
- 阶段 1 会把现有权限不足和资源不存在响应从统一 HTTP 400 修正为 403 和 404；
  业务错误码保持不变，该有意的状态码兼容影响必须在 Pull Request 中说明；
- 每个 Pull Request 必须列出接口、迁移、身份和角色、Redis Key、兼容影响、
  测试结果以及跨服务假设；
- 仓库负责人合并前执行根构建和 Gateway 冒烟检查；
- 如果阶段需要修改公共模块、Gateway 或基础设施，必须从当前阶段移出并先与仓库
  负责人协调。

## 最终验收标准

- 协作指南中规定的动态、关注、点赞、评论、收藏和 Feed 功能全部可用；
- 管理员可以隐藏和恢复动态，普通用户不能绕过可见性规则；
- 唯一约束、事务和原子 SQL 保证并发下没有重复关系或负计数；
- Redis 不可用时接口仍以 MySQL 为权威来源正确工作；
- Feed 使用稳定游标，不返回未关注作者或不可见动态；
- 错误码和 HTTP 状态稳定，响应不泄露内部信息；
- 健康检查、业务指标和请求日志可用于定位故障；
- 全部迁移能在空 MySQL 数据库中顺序执行；
- 单元、Web、Mapper、迁移和集成测试全部通过；
- 所有变更均位于 `foodhub-social`，没有跨服务持久化依赖；
- 中文接口、迁移、缓存和运维文档完整且与实现一致。
