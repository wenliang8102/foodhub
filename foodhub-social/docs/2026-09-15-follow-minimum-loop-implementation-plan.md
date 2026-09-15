# Social 关注最小闭环实施计划

> **面向执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或
> `superpowers:executing-plans`，严格按任务顺序逐项实施，并使用复选框跟踪进度。

**目标：** 在不修改 Merchant、Auth 和公共模块的前提下，为 `foodhub-social`
实现关注、取消关注、关注列表和粉丝列表的最小闭环。

**架构：** 遵循
`FollowController -> FollowService -> FollowMapper -> user_follow` 分层，Entity
和 VO 分离。当前用户只取自网关注入的 `X-User-Id`；Social 只保存用户 ID，
不访问 Auth 数据库，也不调用 Auth 服务。

**技术栈：** Java 21、Spring Boot 3.5、Spring MVC、MyBatis-Plus、MySQL 8、
Flyway、JUnit 5、Mockito、MockMvc。

---

## 文件结构

本轮只允许创建或修改以下文件：

```text
foodhub-social/
  src/main/java/com/foodhub/social/
    controller/FollowController.java
    entity/FollowEntity.java
    mapper/FollowMapper.java
    service/FollowService.java
    vo/FollowPageView.java
    vo/FollowView.java
  src/main/resources/db/migration/
    V2__create_user_follow_table.sql
  src/test/java/com/foodhub/social/
    controller/FollowControllerWebMvcTest.java
    mapper/FollowMapperContractTest.java
    migration/SocialMigrationContractTest.java
    service/FollowServiceTest.java
```

现有 `SocialApplication` 已配置
`@MapperScan("com.foodhub.social.mapper")` 和 `GlobalExceptionHandler`，本轮无需
修改。禁止修改：

```text
foodhub-merchant/**
foodhub-common/**
foodhub-auth/**
foodhub-gateway/**
foodhub-coupon/**
foodhub-order/**
infrastructure/**
pom.xml
```

## 任务 1：创建关注关系表

**文件：**

- 修改：`foodhub-social/src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java`
- 新建：`foodhub-social/src/main/resources/db/migration/V2__create_user_follow_table.sql`

- [ ] **步骤 1：在迁移契约测试中增加失败用例**

在 `SocialMigrationContractTest` 中加入以下测试，并保留已有的帖子迁移测试：

```java
@Test
void followMigrationDefinesConstraintsAndPaginationIndexes() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(
            "/db/migration/V2__create_user_follow_table.sql")) {
        assertNotNull(stream, "关注关系迁移文件必须存在");
        String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE user_follow"));
        assertTrue(sql.contains("follower_id BIGINT NOT NULL"));
        assertTrue(sql.contains("following_id BIGINT NOT NULL"));
        assertTrue(sql.contains(
                "UNIQUE KEY uk_user_follow_pair (follower_id, following_id)"));
        assertTrue(sql.contains(
                "INDEX idx_user_following_page (follower_id, created_at, id)"));
        assertTrue(sql.contains(
                "INDEX idx_user_followers_page (following_id, created_at, id)"));
        assertTrue(sql.contains("CHECK (follower_id <> following_id)"));
    }
}
```

- [ ] **步骤 2：运行测试并确认失败原因正确**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=SocialMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：新增测试失败，原因是
`/db/migration/V2__create_user_follow_table.sql` 不存在。

- [ ] **步骤 3：创建 V2 Flyway 迁移**

创建 `V2__create_user_follow_table.sql`：

```sql
CREATE TABLE user_follow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_follow_pair (follower_id, following_id),
    INDEX idx_user_following_page (follower_id, created_at, id),
    INDEX idx_user_followers_page (following_id, created_at, id),
    CONSTRAINT chk_user_follow_follower_id CHECK (follower_id > 0),
    CONSTRAINT chk_user_follow_following_id CHECK (following_id > 0),
    CONSTRAINT chk_user_follow_not_self CHECK (follower_id <> following_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
```

- [ ] **步骤 4：重新运行迁移契约测试**

执行步骤 2 的命令。

预期：`SocialMigrationContractTest` 中的两个测试全部通过，Maven 返回
`BUILD SUCCESS`。

- [ ] **步骤 5：提交数据库迁移**

```powershell
git add foodhub-social/src/main/resources/db/migration/V2__create_user_follow_table.sql `
  foodhub-social/src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java
git commit -m "feat(social): add follow table migration"
```

## 任务 2：实现关注实体和 Mapper

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/mapper/FollowMapperContractTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/entity/FollowEntity.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/mapper/FollowMapper.java`

- [ ] **步骤 1：编写 Mapper 失败测试**

```java
package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowMapperContractTest {

    @Test
    void insertAndDeleteUseTheOwnedFollowTable() throws Exception {
        assertTrue(BaseMapper.class.isAssignableFrom(FollowMapper.class));

        Insert insert = FollowMapper.class
                .getMethod("insertIgnore", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Insert.class);
        String insertSql = String.join(" ", insert.value());
        assertTrue(insertSql.contains("INSERT IGNORE INTO user_follow"));
        assertTrue(insertSql.contains("follower_id, following_id, created_at"));

        Delete delete = FollowMapper.class
                .getMethod("deleteRelationship", long.class, long.class)
                .getAnnotation(Delete.class);
        String deleteSql = String.join(" ", delete.value());
        assertTrue(deleteSql.contains("DELETE FROM user_follow"));
        assertTrue(deleteSql.contains("follower_id = #{followerId}"));
        assertTrue(deleteSql.contains("following_id = #{followingId}"));
    }

    @Test
    void followingQueryUsesStableNewestFirstPagination() throws Exception {
        Select select = FollowMapper.class
                .getMethod("selectFollowingPage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("WHERE follower_id = #{followerId}"));
        assertTrue(sql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }

    @Test
    void followersQueryUsesStableNewestFirstPagination() throws Exception {
        Select select = FollowMapper.class
                .getMethod("selectFollowersPage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("WHERE following_id = #{followingId}"));
        assertTrue(sql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }
}
```

- [ ] **步骤 2：运行测试并确认编译失败**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=FollowMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `FollowMapper`。

- [ ] **步骤 3：创建关注持久化实体**

```java
package com.foodhub.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_follow")
public class FollowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long followerId;
    private Long followingId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFollowerId() { return followerId; }
    public void setFollowerId(Long followerId) { this.followerId = followerId; }
    public Long getFollowingId() { return followingId; }
    public void setFollowingId(Long followingId) { this.followingId = followingId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **步骤 4：实现关注 Mapper**

```java
package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.FollowEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<FollowEntity> {

    @Insert("""
            INSERT IGNORE INTO user_follow (follower_id, following_id, created_at)
            VALUES (#{followerId}, #{followingId}, #{createdAt})
            """)
    int insertIgnore(@Param("followerId") long followerId,
                     @Param("followingId") long followingId,
                     @Param("createdAt") LocalDateTime createdAt);

    @Delete("""
            DELETE FROM user_follow
            WHERE follower_id = #{followerId}
              AND following_id = #{followingId}
            """)
    int deleteRelationship(@Param("followerId") long followerId,
                           @Param("followingId") long followingId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId}")
    long countFollowing(@Param("followerId") long followerId);

    @Select("""
            SELECT id,
                   follower_id AS followerId,
                   following_id AS followingId,
                   created_at AS createdAt
            FROM user_follow
            WHERE follower_id = #{followerId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FollowEntity> selectFollowingPage(@Param("followerId") long followerId,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM user_follow WHERE following_id = #{followingId}")
    long countFollowers(@Param("followingId") long followingId);

    @Select("""
            SELECT id,
                   follower_id AS followerId,
                   following_id AS followingId,
                   created_at AS createdAt
            FROM user_follow
            WHERE following_id = #{followingId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FollowEntity> selectFollowersPage(@Param("followingId") long followingId,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);
}
```

- [ ] **步骤 5：重新运行 Mapper 契约测试**

执行步骤 2 的命令。

预期：3 个 Mapper 契约测试全部通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 6：提交实体和 Mapper**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/entity/FollowEntity.java `
  foodhub-social/src/main/java/com/foodhub/social/mapper/FollowMapper.java `
  foodhub-social/src/test/java/com/foodhub/social/mapper/FollowMapperContractTest.java
git commit -m "feat(social): add follow persistence mapper"
```

## 任务 3：实现关注业务服务和响应模型

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/service/FollowServiceTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/vo/FollowView.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/vo/FollowPageView.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/service/FollowService.java`

- [ ] **步骤 1：编写 Service 失败测试**

```java
package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.FollowEntity;
import com.foodhub.social.mapper.FollowMapper;
import com.foodhub.social.vo.FollowPageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowMapper followMapper;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService(followMapper);
    }

    @Test
    void followCreatesRelationshipForGatewayUser() {
        followService.follow(7L, 9L);

        verify(followMapper).insertIgnore(eq(7L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void followTreatsDuplicateInsertAsSuccess() {
        when(followMapper.insertIgnore(eq(7L), eq(9L), any(LocalDateTime.class)))
                .thenReturn(0);

        followService.follow(7L, 9L);

        verify(followMapper).insertIgnore(eq(7L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void followRejectsSelfRelationship() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> followService.follow(7L, 7L));

        assertEquals("FOLLOW_SELF_NOT_ALLOWED", exception.getCode());
        verify(followMapper, never()).insertIgnore(anyLong(), anyLong(), any());
    }

    @Test
    void followRejectsInvalidCurrentUser() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> followService.follow(null, 9L));

        assertEquals("INVALID_USER_ID", exception.getCode());
        verify(followMapper, never()).insertIgnore(anyLong(), anyLong(), any());
    }

    @Test
    void followRejectsInvalidTargetUser() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> followService.follow(7L, 0L));

        assertEquals("INVALID_USER_ID", exception.getCode());
        verify(followMapper, never()).insertIgnore(anyLong(), anyLong(), any());
    }

    @Test
    void unfollowDeletesRelationship() {
        followService.unfollow(7L, 9L);

        verify(followMapper).deleteRelationship(7L, 9L);
    }

    @Test
    void unfollowTreatsMissingRelationshipAsSuccess() {
        when(followMapper.deleteRelationship(7L, 9L)).thenReturn(0);

        followService.unfollow(7L, 9L);

        verify(followMapper).deleteRelationship(7L, 9L);
    }

    @Test
    void followingReturnsTargetsAndPaginationMetadata() {
        when(followMapper.countFollowing(7L)).thenReturn(1L);
        when(followMapper.selectFollowingPage(7L, 20L, 20))
                .thenReturn(List.of(follow(1L, 7L, 9L)));

        FollowPageView result = followService.following(7L, 2, 20);

        assertEquals(2, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(1L, result.total());
        assertEquals(9L, result.items().getFirst().userId());
        assertEquals("+08:00", result.items().getFirst()
                .followedAt().getOffset().toString());
    }

    @Test
    void followersReturnsSources() {
        when(followMapper.countFollowers(9L)).thenReturn(1L);
        when(followMapper.selectFollowersPage(9L, 0L, 20))
                .thenReturn(List.of(follow(1L, 7L, 9L)));

        FollowPageView result = followService.followers(9L, 1, 20);

        assertEquals(7L, result.items().getFirst().userId());
    }

    @Test
    void emptyFollowingListSkipsPageQuery() {
        when(followMapper.countFollowing(7L)).thenReturn(0L);

        FollowPageView result = followService.following(7L, 1, 20);

        assertEquals(0L, result.total());
        assertEquals(List.of(), result.items());
        verify(followMapper, never()).selectFollowingPage(anyLong(), anyLong(), anyInt());
    }

    @Test
    void listRejectsInvalidPagination() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> followService.followers(7L, 0, 101));

        assertEquals("VALIDATION_ERROR", exception.getCode());
        verify(followMapper, never()).countFollowers(anyLong());
    }

    private FollowEntity follow(long id, long followerId, long followingId) {
        FollowEntity follow = new FollowEntity();
        follow.setId(id);
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        follow.setCreatedAt(LocalDateTime.of(2026, 9, 15, 12, 0));
        return follow;
    }
}
```

- [ ] **步骤 2：运行测试并确认编译失败**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=FollowServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `FollowService`、`FollowView` 或
`FollowPageView`。

- [ ] **步骤 3：定义关注列表响应模型**

创建 `FollowView.java`：

```java
package com.foodhub.social.vo;

import java.time.OffsetDateTime;

public record FollowView(Long userId, OffsetDateTime followedAt) {
}
```

创建 `FollowPageView.java`：

```java
package com.foodhub.social.vo;

import java.util.List;

public record FollowPageView(
        int page,
        int pageSize,
        long total,
        List<FollowView> items) {
}
```

- [ ] **步骤 4：实现关注 Service**

`DELETE /api/follows/{userId}` 对相同用户 ID 也保持幂等成功，因为自关注关系不
可能合法存在；只有 `POST` 对自关注返回 `FOLLOW_SELF_NOT_ALLOWED`。

```java
package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.FollowEntity;
import com.foodhub.social.mapper.FollowMapper;
import com.foodhub.social.vo.FollowPageView;
import com.foodhub.social.vo.FollowView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class FollowService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final FollowMapper followMapper;

    public FollowService(FollowMapper followMapper) {
        this.followMapper = followMapper;
    }

    @Transactional
    public void follow(Long currentUserId, long followingId) {
        long followerId = requireCurrentUserId(currentUserId);
        validateTargetUserId(followingId);
        if (followerId == followingId) {
            throw new BusinessException(
                    "FOLLOW_SELF_NOT_ALLOWED", "不能关注自己");
        }
        followMapper.insertIgnore(
                followerId,
                followingId,
                LocalDateTime.now(DATABASE_ZONE));
    }

    @Transactional
    public void unfollow(Long currentUserId, long followingId) {
        long followerId = requireCurrentUserId(currentUserId);
        validateTargetUserId(followingId);
        followMapper.deleteRelationship(followerId, followingId);
    }

    public FollowPageView following(Long currentUserId, int page, int pageSize) {
        long followerId = requireCurrentUserId(currentUserId);
        validatePagination(page, pageSize);
        long total = followMapper.countFollowing(followerId);
        if (total == 0) {
            return new FollowPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<FollowView> items = followMapper
                .selectFollowingPage(followerId, offset, pageSize).stream()
                .map(follow -> toView(follow.getFollowingId(), follow))
                .toList();
        return new FollowPageView(page, pageSize, total, items);
    }

    public FollowPageView followers(Long currentUserId, int page, int pageSize) {
        long followingId = requireCurrentUserId(currentUserId);
        validatePagination(page, pageSize);
        long total = followMapper.countFollowers(followingId);
        if (total == 0) {
            return new FollowPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<FollowView> items = followMapper
                .selectFollowersPage(followingId, offset, pageSize).stream()
                .map(follow -> toView(follow.getFollowerId(), follow))
                .toList();
        return new FollowPageView(page, pageSize, total, items);
    }

    private long requireCurrentUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw invalidUserId();
        }
        return userId;
    }

    private void validateTargetUserId(long userId) {
        if (userId <= 0) {
            throw invalidUserId();
        }
    }

    private BusinessException invalidUserId() {
        return new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }

    private FollowView toView(Long relatedUserId, FollowEntity follow) {
        return new FollowView(
                relatedUserId,
                follow.getCreatedAt().atZone(DATABASE_ZONE).toOffsetDateTime());
    }
}
```

- [ ] **步骤 5：重新运行 Service 测试**

执行步骤 2 的命令。

预期：11 个 Service 测试全部通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 6：提交业务服务和响应模型**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/service/FollowService.java `
  foodhub-social/src/main/java/com/foodhub/social/vo/FollowView.java `
  foodhub-social/src/main/java/com/foodhub/social/vo/FollowPageView.java `
  foodhub-social/src/test/java/com/foodhub/social/service/FollowServiceTest.java
git commit -m "feat(social): implement follow business service"
```

## 任务 4：实现关注 HTTP 接口

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/controller/FollowControllerWebMvcTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/controller/FollowController.java`

- [ ] **步骤 1：编写 Controller 失败测试**

```java
package com.foodhub.social.controller;

import com.foodhub.common.web.GlobalExceptionHandler;
import com.foodhub.social.service.FollowService;
import com.foodhub.social.vo.FollowPageView;
import com.foodhub.social.vo.FollowView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = FollowControllerWebMvcTest.WebTestApplication.class,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        })
@AutoConfigureMockMvc
class FollowControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowService followService;

    @Test
    void followUsesGatewayUserIdAndPathTarget() throws Exception {
        mockMvc.perform(post("/api/follows/9").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(followService).follow(7L, 9L);
    }

    @Test
    void followRejectsMissingUserHeader() throws Exception {
        mockMvc.perform(post("/api/follows/9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(followService);
    }

    @Test
    void followRejectsMalformedUserHeader() throws Exception {
        mockMvc.perform(post("/api/follows/9")
                        .header("X-User-Id", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(followService);
    }

    @Test
    void followRejectsMalformedTargetUserId() throws Exception {
        mockMvc.perform(post("/api/follows/not-a-number")
                        .header("X-User-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(followService);
    }

    @Test
    void unfollowUsesGatewayUserIdAndPathTarget() throws Exception {
        mockMvc.perform(delete("/api/follows/9").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(followService).unfollow(7L, 9L);
    }

    @Test
    void followingUsesDefaultPagination() throws Exception {
        when(followService.following(7L, 1, 20)).thenReturn(pageView());

        mockMvc.perform(get("/api/follows/following")
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(9))
                .andExpect(jsonPath("$.data.items[0].followedAt")
                        .value("2026-09-15T12:00:00+08:00"));
    }

    @Test
    void followersUsesRequestedPagination() throws Exception {
        when(followService.followers(7L, 2, 10))
                .thenReturn(new FollowPageView(2, 10, 1, List.of()));

        mockMvc.perform(get("/api/follows/followers?page=2&pageSize=10")
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(10));

        verify(followService).followers(7L, 2, 10);
    }

    @Test
    void listRejectsInvalidPaginationBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/api/follows/following?page=0&pageSize=101")
                        .header("X-User-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(followService);
    }

    private FollowPageView pageView() {
        return new FollowPageView(
                1,
                20,
                1,
                List.of(new FollowView(
                        9L,
                        OffsetDateTime.parse("2026-09-15T12:00:00+08:00"))));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
    })
    @Import(FollowController.class)
    static class WebTestApplication {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
```

- [ ] **步骤 2：运行测试并确认编译失败**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=FollowControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `FollowController`。

- [ ] **步骤 3：实现关注 Controller**

```java
package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.service.FollowService;
import com.foodhub.social.vo.FollowPageView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{userId}")
    public ApiResponse<Void> follow(
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        followService.follow(parseUserId(userIdHeader), parseUserId(userId));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unfollow(
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        followService.unfollow(parseUserId(userIdHeader), parseUserId(userId));
        return ApiResponse.success(null);
    }

    @GetMapping("/following")
    public ApiResponse<FollowPageView> following(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePagination(page, pageSize);
        return ApiResponse.success(
                followService.following(parseUserId(userIdHeader), page, pageSize));
    }

    @GetMapping("/followers")
    public ApiResponse<FollowPageView> followers(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePagination(page, pageSize);
        return ApiResponse.success(
                followService.followers(parseUserId(userIdHeader), page, pageSize));
    }

    private long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUserId();
        }
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw invalidUserId();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
    }

    private BusinessException invalidUserId() {
        return new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }
}
```

- [ ] **步骤 4：重新运行 Controller 测试**

执行步骤 2 的命令。

预期：8 个 Controller 测试全部通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 5：提交 HTTP 接口**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/controller/FollowController.java `
  foodhub-social/src/test/java/com/foodhub/social/controller/FollowControllerWebMvcTest.java
git commit -m "feat(social): expose follow minimum loop endpoints"
```

## 任务 5：完成回归与边界验收

**文件：**

- 不新增业务文件；仅验证任务 1 至任务 4 的实现。

- [ ] **步骤 1：运行完整 Social 聚焦测试**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

预期：帖子和关注相关测试全部通过，Maven Reactor 返回 `BUILD SUCCESS`，测试
汇总中 `Failures: 0, Errors: 0`。

- [ ] **步骤 2：检查格式和工作区内容**

```powershell
git diff --check
git status --short
git diff --name-only main...HEAD
```

预期：

- `git diff --check` 没有输出；
- 所有功能和文档变更均位于 `foodhub-social/`；
- 不包含 `target/`、`.env`、日志或 IDE 文件；
- 不包含 `foodhub-merchant/`、共享模块或根 `pom.xml` 变更。

- [ ] **步骤 3：检查跨服务 Java 依赖**

```powershell
rg -n "com\.foodhub\.(merchant|auth|coupon|order)" foodhub-social/src
```

预期：没有匹配结果。Social 不导入其他业务服务的 Entity、Mapper 或 Service。

- [ ] **步骤 4：核对关注接口和数据表契约**

```powershell
rg -n "(/api/follows|user_follow|X-User-Id|INSERT IGNORE)" foodhub-social/src
```

预期：四个接口由 `FollowController` 提供；写操作使用 `X-User-Id`；Mapper 只
访问 `user_follow`；幂等关注使用 `INSERT IGNORE`。

- [ ] **步骤 5：检查最终提交记录与工作区**

```powershell
git log --oneline --decorate -8
git status --short --branch
```

预期：任务 1 至任务 4 各自有聚焦提交，工作区干净，当前分支为
`codex/social-follow-minimum-loop`。

## Pull Request 交接说明

关注闭环完成并且帖子最小闭环 PR 已合并后，先同步 `main`，再为当前分支创建
单独 Pull Request。PR 使用以下中文说明：

```markdown
## 实现内容

- 新增关注和取消关注接口，重复操作保持幂等
- 新增关注列表和粉丝列表分页查询
- 新增 Social `user_follow` 表迁移与唯一约束
- 列表只返回用户 ID 和关注建立时间

## 接口

- `POST /api/follows/{userId}`：需要 `X-User-Id`
- `DELETE /api/follows/{userId}`：需要 `X-User-Id`
- `GET /api/follows/following`：需要 `X-User-Id`
- `GET /api/follows/followers`：需要 `X-User-Id`

## 边界

- 未修改 `foodhub-merchant` 或任何共享模块
- 未读取 Auth 数据库，也未调用 Auth 服务
- 未实现 Feed、点赞、评论或收藏

## 验证

- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test`
- 全部测试通过，`Failures: 0, Errors: 0`
```
