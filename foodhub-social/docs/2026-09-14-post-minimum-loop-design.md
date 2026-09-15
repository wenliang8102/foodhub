# Social 帖子最小闭环设计

## 实现范围

`foodhub-social` 第一个交付版本只实现帖子最小闭环：

- 发布帖子；
- 分页查询可见帖子；
- 查看单个可见帖子；
- 帖子作者软删除自己的帖子。

本次不实现关注、点赞、评论、收藏或关注动态流。不修改或导入
`foodhub-merchant` 中的代码，也不修改公共模块、网关、认证服务、基础设施或
根 Maven 构建配置。

## 系统架构

实现遵循仓库规定的分层结构：

```text
PostController -> PostService -> PostMapper -> foodhub_social.post
```

请求 DTO、持久化实体和响应 VO 相互分离。

- `PostController`：定义四个 `/api/posts` 接口，并在受保护操作中从
  `X-User-Id` 解析当前用户。
- `CreatePostRequest`：校验创建帖子的请求体。
- `PostService`：负责创建、分页、详情查询、作者权限校验、JSON 转换和软删除。
- `PostMapper`：负责访问 Social 服务自己的 `post` 表。
- `PostEntity`：只表示持久化数据，不直接作为接口响应。
- `PostView` 和 `PostPageView`：定义对外响应模型。
- `SocialApplication`：导入现有公共异常处理器，并扫描 Social 的 Mapper 包。

所有新增和修改的文件都位于 `foodhub-social` 内。

## 数据模型

新增数据库迁移文件
`foodhub-social/src/main/resources/db/migration/V1__create_social_tables.sql`，
并创建 `post` 表：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | `BIGINT` | 自增主键 |
| `author_id` | `BIGINT` | 必填，从 `X-User-Id` 获取 |
| `content` | `VARCHAR(2000)` | 必填，非空白帖子正文 |
| `image_urls` | `JSON` | 可选的图片 URL 数组 |
| `merchant_id` | `BIGINT` | 可选，只保存正数标识引用 |
| `status` | `VARCHAR(16)` | 值为 `VISIBLE` 或 `DELETED` |
| `published_at` | `DATETIME` | 必填，发布时间 |
| `updated_at` | `DATETIME` | 必填，最后更新时间 |
| `deleted_at` | `DATETIME` | 软删除时记录删除时间 |

为 `(status, published_at, id)` 创建组合索引，用于可见帖子的分页查询。
Social 只保存 `merchant_id`，不会查询 Merchant 数据库、调用 Merchant 服务，
也不会验证对应商户是否真实存在。

## 输入契约

创建帖子的请求格式：

```json
{
  "content": "分享一家本地餐厅",
  "imageUrls": ["https://example.com/image.jpg"],
  "merchantId": 123
}
```

校验规则：

- 正文去除首尾空格后长度为 1 到 2000 个字符；
- 最多允许 9 个图片 URL；
- 每个图片 URL 最长为 2048 个字符，并且必须是绝对 `http` 或 `https` URL；
- `merchantId` 如有提供，必须为正数；
- 客户端不能通过请求体或路径提供当前操作用户的 ID。

图片地址在 API 层使用 `List<String>` 表示，持久化前编码为 JSON。

## HTTP 接口

### 发布帖子

`POST /api/posts`

必须提供 `X-User-Id`。创建一个可见帖子，并使用 `ApiResponse` 包装返回
`PostView`。

### 帖子列表

`GET /api/posts?page=1&pageSize=20`

公开接口。`page` 从 1 开始；`pageSize` 默认为 20，最大为 100。只返回
`VISIBLE` 状态的记录，按照 `published_at DESC, id DESC` 排序。响应包含
`page`、`pageSize`、`total` 和 `items`。

### 帖子详情

`GET /api/posts/{postId}`

公开接口。只返回可见帖子。帖子不存在或已经删除时返回
`POST_NOT_FOUND`。

### 删除帖子

`DELETE /api/posts/{postId}`

必须提供 `X-User-Id`。只有作者可以删除帖子。删除时将状态改为
`DELETED`，并记录 `deleted_at`。重复删除返回 `POST_NOT_FOUND`，其他用户
尝试删除时返回 `POST_FORBIDDEN`。

## 响应模型

`PostView` 对外提供以下字段：

- `id`；
- `authorId`；
- `content`；
- `imageUrls`；
- `merchantId`；
- `publishedAt`。

所有响应使用现有的 `ApiResponse`。`publishedAt` 使用 ISO-8601 格式，并带有
明确的 `+08:00` 时区偏移，与数据库配置的 `Asia/Shanghai` 时区保持一致。

## 错误处理

业务错误使用现有的 `BusinessException` 和 `GlobalExceptionHandler`：

| 错误码 | 含义 |
| --- | --- |
| `INVALID_USER_ID` | `X-User-Id` 缺失、格式错误或不是正数 |
| `INVALID_IMAGE_URL` | 图片地址不是绝对 HTTP(S) URL |
| `POST_NOT_FOUND` | 帖子不存在或不再可见 |
| `POST_FORBIDDEN` | 当前用户不是帖子作者 |
| `POST_DATA_INVALID` | 无法解析数据库中保存的图片 JSON |

Bean Validation 参数校验错误继续使用公共的 `VALIDATION_ERROR` 响应。

## 持久化流程

创建帖子时，系统去除正文首尾空格、验证图片 URL、序列化图片列表、设置网关
提供的用户 ID，并在一个事务中插入 `VISIBLE` 状态的记录。所有读取操作只查询
可见记录。分页采用 Mapper 中明确的总数 SQL 和分页 SQL，因此不需要修改公共
MyBatis 分页配置。

删除帖子时，先读取可见帖子并验证作者，再执行带条件的软删除更新。如果并发
请求已经先一步删除该帖子，则返回 `POST_NOT_FOUND`。

## 测试策略

开发过程采用测试驱动方式。

Controller 测试覆盖：创建成功、请求体校验失败、身份头缺失或无效、分页参数
有效与无效、查询详情以及删除操作委派。

Service 测试覆盖：设置正确作者、正文去除首尾空格、图片 JSON 转换、图片 URL
无效、可见帖子分页、详情不存在、作者成功删除、非作者禁止删除，以及并发或
重复删除。

针对 Social 模块及其依赖的验证命令：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

交付前必须使用 `git diff` 确认 `foodhub-merchant` 和公共模块没有发生任何修改。
