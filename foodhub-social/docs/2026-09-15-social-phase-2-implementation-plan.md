# Social 阶段 2 实施计划

> **面向执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或
> `superpowers:executing-plans`，严格按任务顺序逐项实施，并使用复选框跟踪进度。

**目标：** 为可见动态增加幂等点赞、取消点赞、收藏、取消收藏和收藏列表，并让
动态响应携带点赞数、收藏数以及登录用户的互动状态。

**架构：** 保持 `Controller -> Service -> Mapper -> foodhub_social` 分层。点赞与
收藏事实表和动态计数在同一事务中更新，唯一约束负责并发防重；所有操作只访问
Social 自有表，不读取 Auth 或 Merchant 数据。

**技术栈：** Java 21、Spring Boot 3.5、Spring MVC、MyBatis-Plus、MySQL 8、
Flyway、JUnit 5、Mockito、MockMvc。

---

## 范围与兼容约束

- 只修改 `foodhub-social`。
- 不修改根 `pom.xml`、公共模块、Gateway、基础设施或其他业务模块。
- 保留全部现有动态和关注接口路径，不删除或重命名已有响应字段。
- `PostView` 只在末尾追加 `likeCount`、`favoriteCount`、`liked`、`favorited`。
- 匿名动态查询中 `liked` 和 `favorited` 为 `null`；合法身份头下返回布尔值。
- 隐藏状态尚未引入，本阶段仅允许对 `VISIBLE` 动态互动。

## 任务 1：增加互动表与动态计数字段

**文件：**

- 修改：`src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java`
- 新建：`src/main/resources/db/migration/V3__create_post_interaction_tables.sql`

- [ ] **步骤 1：先增加失败契约测试**

测试读取 V3 并断言存在以下 SQL：

```sql
ALTER TABLE post ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE post ADD COLUMN favorite_count BIGINT NOT NULL DEFAULT 0;
CREATE TABLE post_like
UNIQUE KEY uk_post_like_user_post (user_id, post_id)
CREATE TABLE post_favorite
UNIQUE KEY uk_post_favorite_user_post (user_id, post_id)
INDEX idx_post_favorite_user_page (user_id, created_at, id)
```

- [ ] **步骤 2：运行 `SocialMigrationContractTest`，确认因 V3 不存在而 RED**
- [ ] **步骤 3：创建 V3，包含正数检查、计数非负检查、Social 内部 `post_id` 外键和分页索引**
- [ ] **步骤 4：重新运行测试并确认 GREEN**
- [ ] **步骤 5：提交 `feat(social): add like and favorite schema`**

## 任务 2：增加互动持久化层

**文件：**

- 新建：`src/test/java/com/foodhub/social/mapper/InteractionMapperContractTest.java`
- 新建：`src/main/java/com/foodhub/social/mapper/InteractionMapper.java`
- 修改：`src/main/java/com/foodhub/social/entity/PostEntity.java`
- 修改：`src/main/java/com/foodhub/social/mapper/PostMapper.java`

- [ ] **步骤 1：先编写 Mapper 契约测试**

测试要求以下方法存在，并检查 SQL 中的可见性和原子计数条件：

```java
int insertLikeIfAbsent(long userId, long postId, LocalDateTime createdAt);
int deleteLike(long userId, long postId);
int insertFavoriteIfAbsent(long userId, long postId, LocalDateTime createdAt);
int deleteFavorite(long userId, long postId);
int incrementLikeCount(long postId);
int decrementLikeCount(long postId);
int incrementFavoriteCount(long postId);
int decrementFavoriteCount(long postId);
boolean existsLike(long userId, long postId);
boolean existsFavorite(long userId, long postId);
long countVisibleFavorites(long userId);
List<PostEntity> selectVisibleFavoritePage(long userId, long offset, int limit);
```

插入使用 `INSERT ... ON DUPLICATE KEY UPDATE id = id`，递减 SQL 必须包含
`like_count > 0` 或 `favorite_count > 0`，收藏列表必须连接 `post` 并过滤
`status = 'VISIBLE'`，按收藏时间和收藏 ID 倒序。

- [ ] **步骤 2：运行 `InteractionMapperContractTest`，确认找不到 Mapper 而 RED**
- [ ] **步骤 3：实现 Mapper，并给 `PostEntity` 增加两个计数字段**
- [ ] **步骤 4：给 `PostMapper` 的所有动态查询补充两个计数字段**
- [ ] **步骤 5：运行 Mapper 契约测试并确认 GREEN**
- [ ] **步骤 6：提交 `feat(social): add interaction persistence layer`**

## 任务 3：实现点赞、收藏与收藏列表业务层

**文件：**

- 新建：`src/test/java/com/foodhub/social/service/InteractionServiceTest.java`
- 新建：`src/main/java/com/foodhub/social/service/InteractionService.java`
- 修改：`src/main/java/com/foodhub/social/service/PostService.java`
- 修改：`src/main/java/com/foodhub/social/vo/PostView.java`

- [ ] **步骤 1：先编写 Service 失败测试**

至少覆盖：首次点赞/收藏更新事实和计数；重复操作不重复计数；取消不存在关系保持
成功；不可见动态返回 `POST_NOT_FOUND`；无效身份返回 `INVALID_USER_ID`；收藏
列表分页和空页短路；动态响应的计数及登录用户状态。

- [ ] **步骤 2：运行 `InteractionServiceTest,PostServiceTest` 并确认 RED**
- [ ] **步骤 3：为 `PostView` 追加字段**

```java
long likeCount,
long favoriteCount,
Boolean liked,
Boolean favorited
```

- [ ] **步骤 4：实现 `InteractionService`**

写操作先确认 `PostMapper.selectVisibleById(postId)` 存在，再调用事实表增删；仅当
事实表影响行数为 1 时调用对应计数增减。所有四个写方法使用 `@Transactional`。
收藏列表校验分页后查询总数和稳定分页结果，并转换为 `PostPageView`。

- [ ] **步骤 5：让 `PostService` 在登录查询中补充 `liked/favorited`，匿名查询返回 `null`**
- [ ] **步骤 6：运行 Service 测试并确认 GREEN**
- [ ] **步骤 7：提交 `feat(social): implement likes and favorites`**

## 任务 4：暴露互动 HTTP 接口

**文件：**

- 新建：`src/test/java/com/foodhub/social/controller/InteractionControllerWebMvcTest.java`
- 新建：`src/main/java/com/foodhub/social/controller/InteractionController.java`
- 修改：`src/main/java/com/foodhub/social/controller/PostController.java`

- [ ] **步骤 1：先编写 Web 失败测试**

覆盖以下接口及缺失/非法 `X-User-Id`、非法动态 ID、分页错误和响应结构：

```text
POST   /api/posts/{postId}/likes
DELETE /api/posts/{postId}/likes
POST   /api/posts/{postId}/favorite
DELETE /api/posts/{postId}/favorite
GET    /api/posts/favorites?page=1&pageSize=20
```

- [ ] **步骤 2：运行 `InteractionControllerWebMvcTest` 并确认 RED**
- [ ] **步骤 3：实现 Controller，所有写操作和收藏列表复用 `SocialRequestIdentity`**
- [ ] **步骤 4：让动态列表和详情接受可选身份头，存在时必须是合法正数**
- [ ] **步骤 5：运行全部 Controller 测试并确认 GREEN**
- [ ] **步骤 6：提交 `feat(social): expose like and favorite APIs`**

## 任务 5：更新中文文档并完成阶段验收

**文件：**

- 修改：`docs/api.md`

- [ ] **步骤 1：补充五个互动接口、幂等语义、响应字段和错误码中文说明**
- [ ] **步骤 2：运行完整 Social 测试**
- [ ] **步骤 3：运行 `git diff --check` 和跨服务导入扫描**
- [ ] **步骤 4：确认相对 `origin/main` 的改动均位于 `foodhub-social`**
- [ ] **步骤 5：提交 `docs(social): document like and favorite APIs`**
- [ ] **步骤 6：提交后再次运行完整 Social 测试**

阶段二完成后不推送、不创建 Pull Request，直接进入阶段三评论体系。
