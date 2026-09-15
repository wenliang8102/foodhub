# Social 接口说明

## 通用约定

- 生产请求通过 Gateway 访问。
- 受保护接口使用 Gateway 注入的 `X-User-Id`。
- 响应统一使用 `ApiResponse`。
- 时间字段使用带 `+08:00` 偏移量的 ISO-8601 格式。
- `page` 从 1 开始，`pageSize` 默认为 20，最大为 100。

## 动态接口

- `POST /api/posts`：发布动态，需要登录。
- `GET /api/posts`：分页查询可见动态。
- `GET /api/posts/{postId}`：查询可见动态详情。
- `DELETE /api/posts/{postId}`：作者删除自己的动态，需要登录。

## 关注接口

- `POST /api/follows/{userId}`：关注用户，需要登录，重复关注返回成功。
- `DELETE /api/follows/{userId}`：取消关注，需要登录，重复取消返回成功。
- `GET /api/follows/following`：查询当前用户关注的人，需要登录。
- `GET /api/follows/followers`：查询当前用户的粉丝，需要登录。

关注和粉丝列表响应项只包含 `userId` 和 `followedAt`，不返回 Auth 用户资料。

## 阶段 1 错误码

| HTTP 状态 | 错误码 | 含义 |
| ---: | --- | --- |
| 400 | `INVALID_USER_ID` | 用户 ID 缺失、格式错误或不是正数 |
| 400 | `VALIDATION_ERROR` | 请求体或分页参数不合法 |
| 400 | `FOLLOW_SELF_NOT_ALLOWED` | 用户尝试关注自己 |
| 403 | `POST_FORBIDDEN` | 当前用户无权删除动态 |
| 404 | `POST_NOT_FOUND` | 动态不存在或不可见 |
| 500 | `INTERNAL_ERROR` | 未预期的服务端错误 |
