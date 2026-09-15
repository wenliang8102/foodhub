# Social 接口说明

## 通用约定

- 生产请求通过 Gateway 访问。
- 受保护接口使用 Gateway 注入的 `X-User-Id`。
- 响应统一使用 `ApiResponse`。
- 时间字段使用带 `+08:00` 偏移量的 ISO-8601 格式。
- `page` 从 1 开始，`pageSize` 默认为 20，最大为 100。

## 动态接口

- `POST /api/posts`：发布动态，需要登录。
- `GET /api/posts`：分页查询可见动态。可选传入 `X-User-Id`，传入时返回当前用户的点赞和收藏状态。
- `GET /api/posts/{postId}`：查询可见动态详情。可选传入 `X-User-Id`，传入时返回当前用户的点赞和收藏状态。
- `DELETE /api/posts/{postId}`：作者删除自己的动态，需要登录。

动态响应字段在原有基础上追加：

- `likeCount`：点赞数。
- `favoriteCount`：收藏数。
- `commentCount`：可见评论数。
- `liked`：当前登录用户是否点赞。匿名请求为 `null`。
- `favorited`：当前登录用户是否收藏。匿名请求为 `null`。

## 关注接口

- `POST /api/follows/{userId}`：关注用户，需要登录，重复关注返回成功。
- `DELETE /api/follows/{userId}`：取消关注，需要登录，重复取消返回成功。
- `GET /api/follows/following`：查询当前用户关注的人，需要登录。
- `GET /api/follows/followers`：查询当前用户的粉丝，需要登录。

关注和粉丝列表响应项只包含 `userId` 和 `followedAt`，不返回 Auth 用户资料。

## 点赞与收藏接口

- `POST /api/posts/{postId}/likes`：点赞动态，需要登录，重复点赞返回成功。
- `DELETE /api/posts/{postId}/likes`：取消点赞，需要登录，重复取消返回成功。
- `POST /api/posts/{postId}/favorite`：收藏动态，需要登录，重复收藏返回成功。
- `DELETE /api/posts/{postId}/favorite`：取消收藏，需要登录，重复取消返回成功。
- `GET /api/posts/favorites`：分页查询当前用户收藏的可见动态，需要登录。

点赞和收藏只允许作用于 `VISIBLE` 动态。隐藏、删除或不存在的动态统一返回
`POST_NOT_FOUND`，避免暴露治理状态。

## 评论接口

- `POST /api/posts/{postId}/comments`：创建评论，需要登录。
- `GET /api/posts/{postId}/comments`：分页查询可见评论，允许匿名访问。
- `DELETE /api/posts/{postId}/comments/{commentId}`：评论作者删除自己的评论，需要登录。

评论内容会去除首尾空格，长度必须为 1 到 500 个字符。评论使用软删除，公开列表
不会返回已删除评论。删除评论会在同一事务中维护动态的 `commentCount`。

## 内容治理接口

- `PATCH /api/posts/{postId}/visibility`：管理员隐藏或恢复动态。

该接口需要同时传入 `X-User-Id` 和 `X-User-Role: ADMIN`。请求体字段：

- `status`：目标状态，只允许 `VISIBLE` 或 `HIDDEN`。
- `reason`：治理原因，去除首尾空格后必须为 1 到 500 个字符。

作者已经删除的动态不能恢复。每次真实状态变更都会写入 `post_moderation_log`
审计记录。隐藏动态不会删除点赞、收藏和评论数据，但所有公开读取、互动写入和评论
写入都会把隐藏动态视为不可见。

## Feed 接口

- `GET /api/feed`：查询当前用户关注作者发布的可见动态，需要登录。

参数：

- `limit`：默认 20，最大 50。
- `beforePublishedAt` 和 `beforePostId`：可选游标，必须同时提供或同时省略。

Feed 使用数据库拉模式和 `published_at DESC, id DESC` 键集分页，只返回关注用户发布
且状态为 `VISIBLE` 的动态。

## 工程治理接口与可观测性

- `PATCH /api/social/maintenance/posts/{postId}/counters`：管理员触发单条动态计数修复。

计数修复以 Social 自有事实表为准，重新计算点赞数、收藏数和可见评论数，并清理
动态详情缓存。

Social 模块内定义 Redis Key，不修改公共 Redis Key 工具：

- `foodhub:post:detail:{postId}`
- `foodhub:post:null:{postId}`
- `foodhub:post:like:{postId}`
- `foodhub:post:favorite:{postId}`
- `foodhub:post:comment:{postId}`

Redis 仅作为 Cache-Aside 加速层；不可用时自动回退 MySQL。Actuator 暴露
`health`、`info` 和 `metrics`。请求日志会写入安全的 `X-Request-Id`，不会记录动态
正文、评论正文或认证凭据。

## 错误码

| HTTP 状态 | 错误码 | 含义 |
| ---: | --- | --- |
| 400 | `INVALID_USER_ID` | 用户 ID 缺失、格式错误或不是正数 |
| 400 | `VALIDATION_ERROR` | 请求体或分页参数不合法 |
| 400 | `FOLLOW_SELF_NOT_ALLOWED` | 用户尝试关注自己 |
| 403 | `POST_FORBIDDEN` | 当前用户无权删除动态 |
| 403 | `COMMENT_FORBIDDEN` | 当前用户无权删除评论 |
| 403 | `MODERATION_FORBIDDEN` | 当前用户不是管理员 |
| 404 | `POST_NOT_FOUND` | 动态不存在或不可见 |
| 404 | `COMMENT_NOT_FOUND` | 评论不存在或不可见 |
| 500 | `INTERNAL_ERROR` | 未预期的服务端错误 |
