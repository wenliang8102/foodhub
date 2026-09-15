# Social 阶段 1 实施计划

> **面向执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或
> `superpowers:executing-plans`，严格按任务顺序逐项实施，并使用复选框跟踪进度。

**目标：** 在最新 `main` 的动态功能基础上，为 `foodhub-social` 建立统一身份解析
和正确 HTTP 错误映射，并交付关注、取消关注、关注列表和粉丝列表。

**架构：** 保持
`Controller -> Service -> Mapper -> foodhub_social` 分层。Social 内部统一解析
Gateway 身份头并使用本地异常处理器；关注关系由 MySQL 唯一约束保证并发幂等，
不读取 Auth 数据，也不缓存关注数据。

**技术栈：** Java 21、Spring Boot 3.5、Spring MVC、MyBatis-Plus、MySQL 8、
Flyway、JUnit 5、Mockito、MockMvc。

---

## 执行前提

动态基础能力 PR #1 已在 GitHub 合并。企业级路线设计和本计划应先通过文档 PR
进入 `main`。开始业务实现前必须同步最新 `main`，创建独立分支，并确认基线
测试通过：

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c codex/social-phase-1
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

预期：当前分支为 `codex/social-phase-1`，最新 `main` 已包含企业级路线设计和
本计划，Social 基线测试全部通过。

## 文件范围

本阶段只允许创建或修改：

```text
foodhub-social/
  docs/
    api.md
    2026-09-15-enterprise-social-roadmap-design.md
    2026-09-15-social-phase-1-implementation-plan.md
  src/main/java/com/foodhub/social/
    SocialApplication.java
    controller/PostController.java
    controller/FollowController.java
    entity/FollowEntity.java
    mapper/FollowMapper.java
    service/FollowService.java
    vo/FollowView.java
    vo/FollowPageView.java
    web/SocialExceptionHandler.java
    web/SocialRequestIdentity.java
  src/main/resources/db/migration/
    V2__create_user_follow_table.sql
  src/test/java/com/foodhub/social/
    SocialApplicationContractTest.java
    controller/PostControllerWebMvcTest.java
    controller/FollowControllerWebMvcTest.java
    mapper/FollowMapperContractTest.java
    migration/SocialMigrationContractTest.java
    service/FollowServiceTest.java
    web/SocialRequestIdentityTest.java
```

禁止修改 `foodhub-merchant`、`foodhub-auth`、`foodhub-common-*`、Gateway、其他
业务服务、基础设施和根 `pom.xml`。

## 任务 1：统一 Social 用户身份解析

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/web/SocialRequestIdentityTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/web/SocialRequestIdentity.java`
- 修改：`foodhub-social/src/main/java/com/foodhub/social/controller/PostController.java`

- [ ] **步骤 1：编写身份解析失败测试**

```java
package com.foodhub.social.web;

import com.foodhub.common.core.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialRequestIdentityTest {

    @Test
    void requireUserIdParsesPositiveHeader() {
        assertEquals(7L, SocialRequestIdentity.requireUserId(" 7 "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "abc", "0", "-1"})
    void requireUserIdRejectsMissingOrInvalidValue(String value) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> SocialRequestIdentity.requireUserId(value));

        assertEquals("INVALID_USER_ID", exception.getCode());
    }
}
```

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=SocialRequestIdentityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `SocialRequestIdentity`。

- [ ] **步骤 3：实现身份解析器**

```java
package com.foodhub.social.web;

import com.foodhub.common.core.BusinessException;

public final class SocialRequestIdentity {

    private SocialRequestIdentity() {
    }

    public static long requireUserId(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUserId();
        }
        try {
            long userId = Long.parseLong(value.trim());
            if (userId <= 0) {
                throw invalidUserId();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
    }

    private static BusinessException invalidUserId() {
        return new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
    }
}
```

- [ ] **步骤 4：让现有 PostController 复用解析器**

在 `PostController` 中新增导入：

```java
import com.foodhub.social.web.SocialRequestIdentity;
```

将创建和删除方法中的 `resolveUserId(userIdHeader)` 替换为：

```java
SocialRequestIdentity.requireUserId(userIdHeader)
```

删除 `resolveUserId` 和 `invalidUserId` 两个私有方法。分页校验仍保留在当前类
中，因此 `BusinessException` 导入继续保留。

- [ ] **步骤 5：运行身份测试和帖子 Controller 回归**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  '-Dtest=SocialRequestIdentityTest,PostControllerWebMvcTest' `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：身份解析测试和现有帖子 Web 测试全部通过。

- [ ] **步骤 6：提交统一身份解析**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/web/SocialRequestIdentity.java `
  foodhub-social/src/main/java/com/foodhub/social/controller/PostController.java `
  foodhub-social/src/test/java/com/foodhub/social/web/SocialRequestIdentityTest.java
git commit -m "refactor(social): unify gateway user identity parsing"
```

## 任务 2：建立 Social 专用 HTTP 错误映射

**文件：**

- 新建：`foodhub-social/src/main/java/com/foodhub/social/web/SocialExceptionHandler.java`
- 修改：`foodhub-social/src/main/java/com/foodhub/social/SocialApplication.java`
- 修改：`foodhub-social/src/test/java/com/foodhub/social/SocialApplicationContractTest.java`
- 修改：`foodhub-social/src/test/java/com/foodhub/social/controller/PostControllerWebMvcTest.java`

- [ ] **步骤 1：先修改 Web 测试以描述 403、404 和参数格式行为**

在 `PostControllerWebMvcTest` 中：

1. 将 `GlobalExceptionHandler` 导入替换为
   `com.foodhub.social.web.SocialExceptionHandler`；
2. 将 `@Import(PostController.class)` 替换为
   `@Import({PostController.class, SocialExceptionHandler.class})`；
3. 删除内部测试应用中的 `GlobalExceptionHandler` Bean；
4. 新增 `BusinessException` 和 `doThrow` 导入；
5. 增加以下测试：

```java
@Test
void detailMapsMissingPostToNotFound() throws Exception {
    when(postService.detail(99L)).thenThrow(
            new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除"));

    mockMvc.perform(get("/api/posts/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
}

@Test
void deleteMapsForbiddenPostToForbidden() throws Exception {
    doThrow(new BusinessException("POST_FORBIDDEN", "只能删除自己发布的帖子"))
            .when(postService).delete(5L, 8L);

    mockMvc.perform(delete("/api/posts/5").header("X-User-Id", "8"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
}

@Test
void listMapsMalformedPaginationToBadRequest() throws Exception {
    mockMvc.perform(get("/api/posts?page=abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
}

@Test
void unexpectedFailureDoesNotExposeInternalMessage() throws Exception {
    when(postService.detail(100L)).thenThrow(
            new IllegalStateException("sensitive database detail"));

    mockMvc.perform(get("/api/posts/100"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("服务器内部错误"));
}
```

- [ ] **步骤 2：修改应用契约失败测试**

将 `SocialApplicationContractTest` 改为：

```java
package com.foodhub.social;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SocialApplicationContractTest {

    @Test
    void applicationScansSocialMappersWithoutSharedExceptionHandlerImport() {
        MapperScan mapperScan = SocialApplication.class.getAnnotation(MapperScan.class);
        assertEquals("com.foodhub.social.mapper", mapperScan.value()[0]);
        assertNull(SocialApplication.class.getAnnotation(Import.class));
    }
}
```

- [ ] **步骤 3：运行测试并确认 RED**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  '-Dtest=SocialApplicationContractTest,PostControllerWebMvcTest' `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `SocialExceptionHandler`；或者应用契约因仍存在
共享处理器 `@Import` 而失败。

- [ ] **步骤 4：实现 SocialExceptionHandler**

```java
package com.foodhub.social.web;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.foodhub.social")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SocialExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {
        return ResponseEntity.status(statusFor(exception.getCode()))
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return badRequest(message);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return badRequest("请求参数格式错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "服务器内部错误"));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", message));
    }

    private HttpStatus statusFor(String code) {
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.endsWith("_FORBIDDEN")) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
```

- [ ] **步骤 5：让 SocialApplication 使用组件扫描发现本地处理器**

将 `SocialApplication.java` 改为：

```java
package com.foodhub.social;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.foodhub.social.mapper")
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }
}
```

- [ ] **步骤 6：重新运行应用和帖子 Web 测试**

执行步骤 3 的命令。

预期：原有帖子测试以及新增的 400、403、404 测试全部通过。

- [ ] **步骤 7：提交异常映射**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/SocialApplication.java `
  foodhub-social/src/main/java/com/foodhub/social/web/SocialExceptionHandler.java `
  foodhub-social/src/test/java/com/foodhub/social/SocialApplicationContractTest.java `
  foodhub-social/src/test/java/com/foodhub/social/controller/PostControllerWebMvcTest.java
git commit -m "feat(social): map business errors to HTTP status"
```

## 任务 3：创建关注关系表

**文件：**

- 修改：`foodhub-social/src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java`
- 新建：`foodhub-social/src/main/resources/db/migration/V2__create_user_follow_table.sql`

- [ ] **步骤 1：增加迁移契约失败测试**

在现有 `SocialMigrationContractTest` 中加入：

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

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=SocialMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：新增测试失败，提示关注关系迁移文件不存在。

- [ ] **步骤 3：创建 V2 迁移**

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

- [ ] **步骤 4：重新运行迁移测试**

执行步骤 2 的命令。

预期：V1 和 V2 迁移契约测试全部通过。

- [ ] **步骤 5：提交数据库迁移**

```powershell
git add foodhub-social/src/main/resources/db/migration/V2__create_user_follow_table.sql `
  foodhub-social/src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java
git commit -m "feat(social): add follow relationship schema"
```

## 任务 4：实现关注持久化层

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
    void insertIsIdempotentAndDeleteTargetsOneRelationship() throws Exception {
        assertTrue(BaseMapper.class.isAssignableFrom(FollowMapper.class));

        Insert insert = FollowMapper.class
                .getMethod("insertIfAbsent", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Insert.class);
        String insertSql = String.join(" ", insert.value());
        assertTrue(insertSql.contains("INSERT INTO user_follow"));
        assertTrue(insertSql.contains("ON DUPLICATE KEY UPDATE id = id"));

        Delete delete = FollowMapper.class
                .getMethod("deleteRelationship", long.class, long.class)
                .getAnnotation(Delete.class);
        String deleteSql = String.join(" ", delete.value());
        assertTrue(deleteSql.contains("follower_id = #{followerId}"));
        assertTrue(deleteSql.contains("following_id = #{followingId}"));
    }

    @Test
    void followingUsesStableNewestFirstPagination() throws Exception {
        Select following = FollowMapper.class
                .getMethod("selectFollowingPage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String followingSql = String.join(" ", following.value());
        assertTrue(followingSql.contains("WHERE follower_id = #{followerId}"));
        assertTrue(followingSql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(followingSql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }

    @Test
    void followersUsesStableNewestFirstPagination() throws Exception {
        Select followers = FollowMapper.class
                .getMethod("selectFollowersPage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String followersSql = String.join(" ", followers.value());
        assertTrue(followersSql.contains("WHERE following_id = #{followingId}"));
        assertTrue(followersSql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(followersSql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }
}
```

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=FollowMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `FollowMapper`。

- [ ] **步骤 3：创建 FollowEntity**

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

- [ ] **步骤 4：创建 FollowMapper**

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
            INSERT INTO user_follow (follower_id, following_id, created_at)
            VALUES (#{followerId}, #{followingId}, #{createdAt})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("followerId") long followerId,
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

- [ ] **步骤 5：重新运行 Mapper 测试**

执行步骤 2 的命令。

预期：3 个 Mapper 契约测试通过。

- [ ] **步骤 6：提交关注持久化层**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/entity/FollowEntity.java `
  foodhub-social/src/main/java/com/foodhub/social/mapper/FollowMapper.java `
  foodhub-social/src/test/java/com/foodhub/social/mapper/FollowMapperContractTest.java
git commit -m "feat(social): add follow persistence layer"
```

## 任务 5：实现关注业务层

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
    void followCreatesRelationship() {
        when(followMapper.insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class)))
                .thenReturn(1);

        followService.follow(7L, 9L);

        verify(followMapper).insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void duplicateFollowIsStillSuccess() {
        when(followMapper.insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class)))
                .thenReturn(0);

        followService.follow(7L, 9L);

        verify(followMapper).insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void followRejectsSelf() {
        BusinessException self = assertThrows(BusinessException.class,
                () -> followService.follow(7L, 7L));
        assertEquals("FOLLOW_SELF_NOT_ALLOWED", self.getCode());

        verify(followMapper, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void followRejectsInvalidTarget() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> followService.follow(7L, 0L));
        assertEquals("INVALID_USER_ID", invalid.getCode());

        verify(followMapper, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void unfollowIsIdempotent() {
        when(followMapper.deleteRelationship(7L, 9L)).thenReturn(0);

        followService.unfollow(7L, 9L);

        verify(followMapper).deleteRelationship(7L, 9L);
    }

    @Test
    void followingReturnsTargetUsersWithOffsetTime() {
        when(followMapper.countFollowing(7L)).thenReturn(1L);
        when(followMapper.selectFollowingPage(7L, 20L, 20))
                .thenReturn(List.of(follow(1L, 7L, 9L)));

        FollowPageView result = followService.following(7L, 2, 20);

        assertEquals(1L, result.total());
        assertEquals(9L, result.items().getFirst().userId());
        assertEquals("+08:00", result.items().getFirst()
                .followedAt().getOffset().toString());
    }

    @Test
    void followersReturnsSourceUsers() {
        when(followMapper.countFollowers(9L)).thenReturn(1L);
        when(followMapper.selectFollowersPage(9L, 0L, 20))
                .thenReturn(List.of(follow(1L, 7L, 9L)));

        FollowPageView result = followService.followers(9L, 1, 20);

        assertEquals(7L, result.items().getFirst().userId());
    }

    @Test
    void emptyListSkipsPageQuery() {
        when(followMapper.countFollowing(7L)).thenReturn(0L);

        FollowPageView result = followService.following(7L, 1, 20);

        assertEquals(List.of(), result.items());
        verify(followMapper, never()).selectFollowingPage(anyLong(), anyLong(), anyInt());
    }

    @Test
    void listRejectsInvalidPagination() {
        BusinessException pagination = assertThrows(BusinessException.class,
                () -> followService.followers(7L, 0, 101));
        assertEquals("VALIDATION_ERROR", pagination.getCode());
    }

    @Test
    void listRejectsInvalidIdentity() {
        BusinessException identity = assertThrows(BusinessException.class,
                () -> followService.following(null, 1, 20));
        assertEquals("INVALID_USER_ID", identity.getCode());
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

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=FollowServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `FollowService` 或关注 VO。

- [ ] **步骤 3：创建响应 VO**

`FollowView.java`：

```java
package com.foodhub.social.vo;

import java.time.OffsetDateTime;

public record FollowView(Long userId, OffsetDateTime followedAt) {
}
```

`FollowPageView.java`：

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

- [ ] **步骤 4：创建 FollowService**

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
        long followerId = requireUserId(currentUserId);
        validateTargetUserId(followingId);
        if (followerId == followingId) {
            throw new BusinessException("FOLLOW_SELF_NOT_ALLOWED", "不能关注自己");
        }
        followMapper.insertIfAbsent(
                followerId,
                followingId,
                LocalDateTime.now(DATABASE_ZONE));
    }

    @Transactional
    public void unfollow(Long currentUserId, long followingId) {
        long followerId = requireUserId(currentUserId);
        validateTargetUserId(followingId);
        followMapper.deleteRelationship(followerId, followingId);
    }

    public FollowPageView following(Long currentUserId, int page, int pageSize) {
        long followerId = requireUserId(currentUserId);
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
        long followingId = requireUserId(currentUserId);
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

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
        return userId;
    }

    private void validateTargetUserId(long userId) {
        if (userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
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

取消关注自己的用户 ID 也返回成功，因为合法的自关注关系不可能存在；只有新增
自关注时返回 `FOLLOW_SELF_NOT_ALLOWED`。

- [ ] **步骤 5：重新运行 Service 测试**

执行步骤 2 的命令。

预期：10 个 Service 测试通过。

- [ ] **步骤 6：提交关注业务层**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/service/FollowService.java `
  foodhub-social/src/main/java/com/foodhub/social/vo/FollowView.java `
  foodhub-social/src/main/java/com/foodhub/social/vo/FollowPageView.java `
  foodhub-social/src/test/java/com/foodhub/social/service/FollowServiceTest.java
git commit -m "feat(social): implement follow relationship service"
```

## 任务 6：实现关注 HTTP 接口

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/controller/FollowControllerWebMvcTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/controller/FollowController.java`

- [ ] **步骤 1：编写 Controller 失败测试**

```java
package com.foodhub.social.controller;

import com.foodhub.social.service.FollowService;
import com.foodhub.social.vo.FollowPageView;
import com.foodhub.social.vo.FollowView;
import com.foodhub.social.web.SocialExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    void followUsesGatewayIdentity() throws Exception {
        mockMvc.perform(post("/api/follows/9").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(followService).follow(7L, 9L);
    }

    @Test
    void unfollowUsesGatewayIdentity() throws Exception {
        mockMvc.perform(delete("/api/follows/9").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(followService).unfollow(7L, 9L);
    }

    @Test
    void followRejectsMissingHeader() throws Exception {
        mockMvc.perform(post("/api/follows/9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(followService);
    }

    @Test
    void followRejectsMalformedTarget() throws Exception {
        mockMvc.perform(post("/api/follows/abc").header("X-User-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(followService);
    }

    @Test
    void followingUsesDefaultPagination() throws Exception {
        when(followService.following(7L, 1, 20)).thenReturn(pageView());

        mockMvc.perform(get("/api/follows/following").header("X-User-Id", "7"))
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
    @Import({FollowController.class, SocialExceptionHandler.class})
    static class WebTestApplication {
    }
}
```

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=FollowControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `FollowController`。

- [ ] **步骤 3：实现 FollowController**

```java
package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.service.FollowService;
import com.foodhub.social.vo.FollowPageView;
import com.foodhub.social.web.SocialRequestIdentity;
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
        followService.follow(
                SocialRequestIdentity.requireUserId(userIdHeader),
                SocialRequestIdentity.requireUserId(userId));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unfollow(
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        followService.unfollow(
                SocialRequestIdentity.requireUserId(userIdHeader),
                SocialRequestIdentity.requireUserId(userId));
        return ApiResponse.success(null);
    }

    @GetMapping("/following")
    public ApiResponse<FollowPageView> following(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        long currentUserId = SocialRequestIdentity.requireUserId(userIdHeader);
        validatePagination(page, pageSize);
        return ApiResponse.success(followService.following(currentUserId, page, pageSize));
    }

    @GetMapping("/followers")
    public ApiResponse<FollowPageView> followers(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        long currentUserId = SocialRequestIdentity.requireUserId(userIdHeader);
        validatePagination(page, pageSize);
        return ApiResponse.success(followService.followers(currentUserId, page, pageSize));
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

预期：7 个 Controller 测试通过。

- [ ] **步骤 5：提交关注接口**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/controller/FollowController.java `
  foodhub-social/src/test/java/com/foodhub/social/controller/FollowControllerWebMvcTest.java
git commit -m "feat(social): expose follow relationship APIs"
```

## 任务 7：补充中文接口文档并完成验收

**文件：**

- 新建：`foodhub-social/docs/api.md`

- [ ] **步骤 1：创建阶段 1 中文接口文档**

文档至少完整写入以下内容：

```markdown
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
```

- [ ] **步骤 2：运行完整 Social 测试**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

预期：动态和关注全部测试通过，Reactor 返回 `BUILD SUCCESS`，测试汇总中
`Failures: 0, Errors: 0`。

- [ ] **步骤 3：检查格式、边界和跨服务依赖**

```powershell
git diff --check
git status --short
git diff --name-only main...HEAD
rg -n "com\.foodhub\.(merchant|auth|coupon|order)" foodhub-social/src
```

预期：

- `git diff --check` 没有输出；
- 变更文件全部位于 `foodhub-social`；
- 不包含 `target`、`.env`、日志或 IDE 文件；
- 跨服务导入搜索没有匹配结果。

- [ ] **步骤 4：核对接口、迁移和身份契约**

```powershell
rg -n "(/api/follows|user_follow|X-User-Id|ON DUPLICATE KEY)" foodhub-social/src
```

预期：四个关注接口存在；关注 Mapper 只访问 `user_follow`；写操作从
`X-User-Id` 获取当前用户；幂等插入由唯一约束和
`ON DUPLICATE KEY UPDATE` 保证。

- [ ] **步骤 5：提交接口文档**

```powershell
git add foodhub-social/docs/api.md
git commit -m "docs(social): document posts and follow APIs"
```

- [ ] **步骤 6：提交后重新运行最终验证**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
git status --short --branch
git diff --name-only main...HEAD
```

预期：构建成功、工作区干净，并且相对 `main` 的所有变更都位于
`foodhub-social`。

## 阶段 1 Pull Request 说明

```markdown
## 实现内容

- 统一 Social 的 Gateway 用户身份解析
- 为 Social 增加正确的 400、403、404 和 500 HTTP 错误映射
- 新增关注、取消关注、关注列表和粉丝列表
- 新增 `user_follow` 迁移、唯一约束和分页索引
- 补充中文接口文档

## 接口

- `POST /api/follows/{userId}`：需要 `X-User-Id`
- `DELETE /api/follows/{userId}`：需要 `X-User-Id`
- `GET /api/follows/following`：需要 `X-User-Id`
- `GET /api/follows/followers`：需要 `X-User-Id`

## 兼容影响

- 现有动态接口路径和响应字段不变
- `POST_FORBIDDEN` 的 HTTP 状态由 400 修正为 403
- `POST_NOT_FOUND` 的 HTTP 状态由 400 修正为 404

## 边界

- 未修改 `foodhub-merchant`、Auth、Gateway 或公共模块
- 未读取 Auth 数据库，也未调用 Auth 服务
- 本阶段未实现点赞、收藏、评论、治理或 Feed

## 验证

- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test`
- 全部测试通过，`Failures: 0, Errors: 0`
```
