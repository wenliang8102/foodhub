# Social 帖子最小闭环实施计划

> **供智能代理执行：** 必须使用 `superpowers:subagent-driven-development`（推荐）或
> `superpowers:executing-plans`，严格按任务顺序逐项实施，并使用复选框跟踪进度。

**目标：** 在不修改 Merchant 和共享模块的前提下，为 `foodhub-social` 实现帖子
发布、分页列表、详情查询和作者软删除的最小闭环。

**架构：** 遵循 `PostController -> PostService -> PostMapper -> post` 分层，
DTO、Entity 和 VO 分离。身份只取自网关的 `X-User-Id`，Social 仅保存可选的
`merchantId` 引用，不访问 Merchant 服务或数据库。

**技术栈：** Java 21、Spring Boot 3.5、Spring MVC、Jakarta Validation、
MyBatis-Plus、MySQL 8、Flyway、Jackson、JUnit 5、Mockito、MockMvc。

---

## 文件结构

只允许创建或修改以下文件：

```text
foodhub-social/
  src/main/java/com/foodhub/social/
    SocialApplication.java
    controller/PostController.java
    dto/CreatePostRequest.java
    entity/PostEntity.java
    mapper/PostMapper.java
    service/PostService.java
    vo/PostPageView.java
    vo/PostView.java
  src/main/resources/db/migration/
    V1__create_social_tables.sql
  src/test/java/com/foodhub/social/
    controller/PostControllerWebMvcTest.java
    dto/CreatePostRequestValidationTest.java
    service/PostServiceTest.java
  src/test/java/com/foodhub/social/migration/
    SocialMigrationContractTest.java
```

禁止修改：

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

## 任务 1：创建 Social 帖子表

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java`
- 新建：`foodhub-social/src/main/resources/db/migration/V1__create_social_tables.sql`

- [ ] **步骤 1：编写迁移契约失败测试**

```java
package com.foodhub.social.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialMigrationContractTest {

    @Test
    void initialMigrationDefinesOwnedPostTableAndVisibilityIndex() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V1__create_social_tables.sql")) {
            assertNotNull(stream, "Social 初始迁移文件必须存在");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("CREATE TABLE post"));
            assertTrue(sql.contains("author_id BIGINT NOT NULL"));
            assertTrue(sql.contains("image_urls JSON NULL"));
            assertTrue(sql.contains("merchant_id BIGINT NULL"));
            assertTrue(sql.contains("status VARCHAR(16) NOT NULL"));
            assertTrue(sql.contains("INDEX idx_post_visibility_time (status, published_at, id)"));
        }
    }
}
```

- [ ] **步骤 2：运行测试并确认失败原因正确**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=SocialMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试失败，提示 `Social 初始迁移文件必须存在`。

- [ ] **步骤 3：创建 Flyway 初始迁移**

```sql
CREATE TABLE post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    image_urls JSON NULL,
    merchant_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_post_visibility_time (status, published_at, id),
    CONSTRAINT chk_post_author_id CHECK (author_id > 0),
    CONSTRAINT chk_post_merchant_id CHECK (merchant_id IS NULL OR merchant_id > 0),
    CONSTRAINT chk_post_status CHECK (status IN ('VISIBLE', 'DELETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
```

- [ ] **步骤 4：重新运行迁移契约测试**

执行步骤 2 的命令。

预期：`SocialMigrationContractTest` 通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 5：提交数据库迁移**

```powershell
git add foodhub-social/src/main/resources/db/migration/V1__create_social_tables.sql `
  foodhub-social/src/test/java/com/foodhub/social/migration/SocialMigrationContractTest.java
git commit -m "feat(social): add post table migration"
```

## 任务 2：定义请求、实体和响应模型

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/dto/CreatePostRequestValidationTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/dto/CreatePostRequest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/entity/PostEntity.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/vo/PostView.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/vo/PostPageView.java`

- [ ] **步骤 1：编写请求校验失败测试**

```java
package com.foodhub.social.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePostRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRequestPassesBeanValidation() {
        CreatePostRequest request = new CreatePostRequest(
                "分享一家本地餐厅",
                List.of("https://example.com/food.jpg"),
                12L);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void blankContentIsRejected() {
        CreatePostRequest request = new CreatePostRequest("   ", List.of(), null);

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void moreThanNineImagesIsRejected() {
        CreatePostRequest request = new CreatePostRequest(
                "正文",
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> "https://example.com/" + index + ".jpg")
                        .toList(),
                null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void nonPositiveMerchantIdIsRejected() {
        CreatePostRequest request = new CreatePostRequest("正文", List.of(), 0L);

        assertFalse(validator.validate(request).isEmpty());
    }
}
```

- [ ] **步骤 2：运行测试并确认编译失败**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=CreatePostRequestValidationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `CreatePostRequest`。

- [ ] **步骤 3：实现创建帖子请求 DTO**

```java
package com.foodhub.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "帖子正文不能为空")
        @Size(max = 2000, message = "帖子正文不能超过 2000 个字符")
        String content,

        @Size(max = 9, message = "帖子图片不能超过 9 张")
        List<@NotBlank(message = "图片地址不能为空")
                @Size(max = 2048, message = "图片地址不能超过 2048 个字符") String> imageUrls,

        @Positive(message = "merchantId 必须为正数")
        Long merchantId) {
}
```

- [ ] **步骤 4：定义持久化实体**

```java
package com.foodhub.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("post")
public class PostEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String content;
    private String imageUrls;
    private Long merchantId;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
```

- [ ] **步骤 5：定义响应 VO**

```java
package com.foodhub.social.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PostView(
        Long id,
        Long authorId,
        String content,
        List<String> imageUrls,
        Long merchantId,
        OffsetDateTime publishedAt) {
}
```

```java
package com.foodhub.social.vo;

import java.util.List;

public record PostPageView(
        int page,
        int pageSize,
        long total,
        List<PostView> items) {
}
```

- [ ] **步骤 6：重新运行 DTO 校验测试**

执行步骤 2 的命令。

预期：4 个测试全部通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 7：提交数据模型**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/dto/CreatePostRequest.java `
  foodhub-social/src/main/java/com/foodhub/social/entity/PostEntity.java `
  foodhub-social/src/main/java/com/foodhub/social/vo/PostView.java `
  foodhub-social/src/main/java/com/foodhub/social/vo/PostPageView.java `
  foodhub-social/src/test/java/com/foodhub/social/dto/CreatePostRequestValidationTest.java
git commit -m "feat(social): define post request and response models"
```

## 任务 3：实现帖子 Mapper

**文件：**

- 新建：`foodhub-social/src/main/java/com/foodhub/social/mapper/PostMapper.java`

- [ ] **步骤 1：定义只访问 Social 帖子表的 Mapper**

```java
package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.PostEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    @Select("""
            SELECT id,
                   author_id AS authorId,
                   content,
                   image_urls AS imageUrls,
                   merchant_id AS merchantId,
                   status,
                   published_at AS publishedAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM post
            WHERE status = 'VISIBLE'
            ORDER BY published_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostEntity> selectVisiblePage(@Param("offset") long offset,
                                       @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM post WHERE status = 'VISIBLE'")
    long countVisible();

    @Select("""
            SELECT id,
                   author_id AS authorId,
                   content,
                   image_urls AS imageUrls,
                   merchant_id AS merchantId,
                   status,
                   published_at AS publishedAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM post
            WHERE id = #{postId} AND status = 'VISIBLE'
            """)
    PostEntity selectVisibleById(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET status = 'DELETED',
                updated_at = #{deletedAt},
                deleted_at = #{deletedAt}
            WHERE id = #{postId}
              AND author_id = #{authorId}
              AND status = 'VISIBLE'
            """)
    int softDelete(@Param("postId") long postId,
                   @Param("authorId") long authorId,
                   @Param("deletedAt") LocalDateTime deletedAt);
}
```

说明：创建操作直接使用 `BaseMapper.insert`，因此不需要额外的 `@Insert`；实现时
应删除上面未使用的 `org.apache.ibatis.annotations.Insert` 导入，保证编译无警告。

- [ ] **步骤 2：编译 Social 模块**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -DskipTests compile
```

预期：Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 3：提交 Mapper**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/mapper/PostMapper.java
git commit -m "feat(social): add post persistence mapper"
```

## 任务 4：实现帖子业务服务

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/service/PostServiceTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/service/PostService.java`

- [ ] **步骤 1：编写 Service 失败测试**

```java
package com.foodhub.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postMapper, new ObjectMapper());
    }

    @Test
    void createUsesGatewayUserAndNormalizesContentAndImages() {
        doAnswer(invocation -> {
            PostEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(postMapper).insert(any(PostEntity.class));

        PostView result = postService.create(7L, new CreatePostRequest(
                "  正文内容  ",
                List.of(" https://example.com/food.jpg "),
                12L));

        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).insert(captor.capture());
        PostEntity saved = captor.getValue();
        assertEquals(7L, saved.getAuthorId());
        assertEquals("正文内容", saved.getContent());
        assertEquals("[\"https://example.com/food.jpg\"]", saved.getImageUrls());
        assertEquals("VISIBLE", saved.getStatus());
        assertEquals(10L, result.id());
        assertEquals(List.of("https://example.com/food.jpg"), result.imageUrls());
    }

    @Test
    void createRejectsInvalidImageUrl() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.create(7L, new CreatePostRequest(
                        "正文", List.of("file:///tmp/a.jpg"), null)));

        assertEquals("INVALID_IMAGE_URL", exception.getCode());
        verify(postMapper, never()).insert(any(PostEntity.class));
    }

    @Test
    void createRejectsInvalidUserId() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.create(0L,
                        new CreatePostRequest("正文", List.of(), null)));

        assertEquals("INVALID_USER_ID", exception.getCode());
    }

    @Test
    void listReturnsVisiblePostsAndPaginationMetadata() {
        when(postMapper.countVisible()).thenReturn(1L);
        when(postMapper.selectVisiblePage(20L, 20)).thenReturn(List.of(post(5L, 7L)));

        PostPageView result = postService.list(2, 20);

        assertEquals(2, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(1L, result.total());
        assertEquals(1, result.items().size());
        assertEquals(5L, result.items().getFirst().id());
    }

    @Test
    void listRejectsInvalidPagination() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.list(0, 101));

        assertEquals("VALIDATION_ERROR", exception.getCode());
    }

    @Test
    void detailReturnsVisiblePost() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));

        PostView result = postService.detail(5L);

        assertEquals(5L, result.id());
        assertEquals(7L, result.authorId());
    }

    @Test
    void detailRejectsMissingOrDeletedPost() {
        when(postMapper.selectVisibleById(5L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.detail(5L));

        assertEquals("POST_NOT_FOUND", exception.getCode());
    }

    @Test
    void deleteAllowsPostAuthor() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(postMapper.softDelete(org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(7L), any(LocalDateTime.class))).thenReturn(1);

        postService.delete(5L, 7L);

        verify(postMapper).softDelete(org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(7L), any(LocalDateTime.class));
    }

    @Test
    void deleteRejectsAnotherUser() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.delete(5L, 8L));

        assertEquals("POST_FORBIDDEN", exception.getCode());
        verify(postMapper, never()).softDelete(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(LocalDateTime.class));
    }

    @Test
    void deleteReportsConcurrentDeletionAsNotFound() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(postMapper.softDelete(org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(7L), any(LocalDateTime.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.delete(5L, 7L));

        assertEquals("POST_NOT_FOUND", exception.getCode());
    }

    private PostEntity post(long id, long authorId) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setContent("正文");
        post.setImageUrls("[]");
        post.setStatus("VISIBLE");
        post.setPublishedAt(LocalDateTime.of(2026, 9, 14, 12, 0));
        post.setUpdatedAt(post.getPublishedAt());
        return post;
    }
}
```

- [ ] **步骤 2：运行测试并确认编译失败**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=PostServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `PostService`。

- [ ] **步骤 3：实现完整帖子业务服务**

```java
package com.foodhub.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PostService {

    private static final String VISIBLE = "VISIBLE";
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final PostMapper postMapper;
    private final ObjectMapper objectMapper;

    public PostService(PostMapper postMapper, ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PostView create(Long authorId, CreatePostRequest request) {
        validateUserId(authorId);
        List<String> imageUrls = normalizeAndValidateImages(request.imageUrls());
        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);

        PostEntity post = new PostEntity();
        post.setAuthorId(authorId);
        post.setContent(request.content().trim());
        post.setImageUrls(writeImages(imageUrls));
        post.setMerchantId(request.merchantId());
        post.setStatus(VISIBLE);
        post.setPublishedAt(now);
        post.setUpdatedAt(now);
        postMapper.insert(post);
        return toView(post);
    }

    public PostPageView list(int page, int pageSize) {
        validatePagination(page, pageSize);
        long total = postMapper.countVisible();
        if (total == 0) {
            return new PostPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<PostView> items = postMapper.selectVisiblePage(offset, pageSize).stream()
                .map(this::toView)
                .toList();
        return new PostPageView(page, pageSize, total, items);
    }

    public PostView detail(long postId) {
        PostEntity post = postId > 0 ? postMapper.selectVisibleById(postId) : null;
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        return toView(post);
    }

    @Transactional
    public void delete(long postId, Long currentUserId) {
        validateUserId(currentUserId);
        PostEntity post = postId > 0 ? postMapper.selectVisibleById(postId) : null;
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        if (!Objects.equals(post.getAuthorId(), currentUserId)) {
            throw new BusinessException("POST_FORBIDDEN", "只能删除自己发布的帖子");
        }
        int updated = postMapper.softDelete(
                postId, currentUserId, LocalDateTime.now(DATABASE_ZONE));
        if (updated != 1) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "X-User-Id 必须为正整数");
        }
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }

    private List<String> normalizeAndValidateImages(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        return imageUrls.stream().map(String::trim).peek(this::validateImageUrl).toList();
    }

    private void validateImageUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null
                    ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https"))
                    || !uri.isAbsolute() || uri.getHost() == null) {
                throw invalidImageUrl();
            }
        } catch (IllegalArgumentException exception) {
            throw invalidImageUrl();
        }
    }

    private BusinessException invalidImageUrl() {
        return new BusinessException(
                "INVALID_IMAGE_URL", "图片地址必须是绝对 HTTP(S) URL");
    }

    private String writeImages(List<String> imageUrls) {
        try {
            return objectMapper.writeValueAsString(imageUrls);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("POST_DATA_INVALID", "无法保存帖子图片数据");
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(json, STRING_LIST));
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new BusinessException("POST_DATA_INVALID", "帖子图片数据格式错误");
        }
    }

    private PostView toView(PostEntity post) {
        if (post.getPublishedAt() == null) {
            throw new BusinessException("POST_DATA_INVALID", "帖子发布时间缺失");
        }
        return new PostView(
                post.getId(),
                post.getAuthorId(),
                post.getContent(),
                readImages(post.getImageUrls()),
                post.getMerchantId(),
                post.getPublishedAt().atZone(DATABASE_ZONE).toOffsetDateTime());
    }
}
```

- [ ] **步骤 4：重新运行 Service 测试**

执行步骤 2 的命令。

预期：10 个测试全部通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 5：提交 Service**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/service/PostService.java `
  foodhub-social/src/test/java/com/foodhub/social/service/PostServiceTest.java
git commit -m "feat(social): implement post business service"
```

## 任务 5：实现帖子 HTTP 接口

**文件：**

- 新建：`foodhub-social/src/test/java/com/foodhub/social/controller/PostControllerWebMvcTest.java`
- 新建：`foodhub-social/src/main/java/com/foodhub/social/controller/PostController.java`

- [ ] **步骤 1：编写 Controller 失败测试**

```java
package com.foodhub.social.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.web.GlobalExceptionHandler;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.service.PostService;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PostControllerWebMvcTest.WebTestApplication.class,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "spring.cloud.discovery.enabled=false"
        })
@AutoConfigureMockMvc
class PostControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

    @Test
    void createUsesGatewayUserIdAndReturnsPost() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "正文", List.of("https://example.com/a.jpg"), 12L);
        when(postService.create(7L, request)).thenReturn(postView());

        mockMvc.perform(post("/api/posts")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.authorId").value(7));

        verify(postService).create(7L, request);
    }

    @Test
    void createRejectsBlankContentBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(postService);
    }

    @Test
    void createRejectsMissingUserHeader() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"正文\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));
    }

    @Test
    void createRejectsMalformedUserHeader() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("X-User-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"正文\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));
    }

    @Test
    void listUsesDefaultPagination() throws Exception {
        when(postService.list(1, 20))
                .thenReturn(new PostPageView(1, 20, 1, List.of(postView())));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/posts?page=0&pageSize=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(postService);
    }

    @Test
    void detailReturnsVisiblePost() throws Exception {
        when(postService.detail(5L)).thenReturn(postView());

        mockMvc.perform(get("/api/posts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.publishedAt")
                        .value("2026-09-14T12:00:00+08:00"));
    }

    @Test
    void deleteUsesGatewayUserId() throws Exception {
        mockMvc.perform(delete("/api/posts/5").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(postService).delete(5L, 7L);
    }

    private PostView postView() {
        return new PostView(
                5L,
                7L,
                "正文",
                List.of("https://example.com/a.jpg"),
                12L,
                OffsetDateTime.parse("2026-09-14T12:00:00+08:00"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
    })
    @Import(PostController.class)
    static class WebTestApplication {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
```

- [ ] **步骤 2：运行测试并确认编译失败**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am `
  -Dtest=PostControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，提示找不到 `PostController`。

- [ ] **步骤 3：实现帖子 Controller**

```java
package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.service.PostService;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ApiResponse<PostView> create(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.create(resolveUserId(userIdHeader), request));
    }

    @GetMapping
    public ApiResponse<PostPageView> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePagination(page, pageSize);
        return ApiResponse.success(postService.list(page, pageSize));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostView> detail(@PathVariable long postId) {
        return ApiResponse.success(postService.detail(postId));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        postService.delete(postId, resolveUserId(userIdHeader));
        return ApiResponse.success(null);
    }

    private Long resolveUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw invalidUserId();
        }
        try {
            long userId = Long.parseLong(userIdHeader);
            if (userId <= 0) {
                throw invalidUserId();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
    }

    private BusinessException invalidUserId() {
        return new BusinessException("INVALID_USER_ID", "X-User-Id 必须为正整数");
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }
}
```

- [ ] **步骤 4：重新运行 Controller 测试**

执行步骤 2 的命令。

预期：8 个测试全部通过，Maven 返回 `BUILD SUCCESS`。

- [ ] **步骤 5：提交 HTTP 接口**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/controller/PostController.java `
  foodhub-social/src/test/java/com/foodhub/social/controller/PostControllerWebMvcTest.java
git commit -m "feat(social): expose post minimum loop endpoints"
```

## 任务 6：装配 Social 应用并完成验收

**文件：**

- 修改：`foodhub-social/src/main/java/com/foodhub/social/SocialApplication.java`

- [ ] **步骤 1：显式装配 Social Mapper 与现有异常处理器**

将 `SocialApplication.java` 修改为：

```java
package com.foodhub.social;

import com.foodhub.common.web.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("com.foodhub.social.mapper")
@Import(GlobalExceptionHandler.class)
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }
}
```

- [ ] **步骤 2：运行全部 Social 测试及依赖构建**

执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

预期：迁移契约、DTO、Service 和 Controller 测试全部通过，Maven 返回
`BUILD SUCCESS`，输出中没有失败或错误测试。

- [ ] **步骤 3：执行代码与边界检查**

```powershell
git diff --check
git status --short
git diff --name-only origin/main...HEAD
```

预期：

- `git diff --check` 没有输出；
- 变更文件全部位于 `foodhub-social/`；
- 不存在 `target/`、`.env`、日志或 IDE 文件；
- 不存在任何 `foodhub-merchant/`、`foodhub-common/` 或根 `pom.xml` 变更。

- [ ] **步骤 4：检查跨服务依赖**

```powershell
rg -n "com\.foodhub\.(merchant|coupon|order)" foodhub-social/src
```

预期：没有匹配结果。Social 只保存 `merchantId` 数值，不导入其他业务服务类型。

- [ ] **步骤 5：提交应用装配**

```powershell
git add foodhub-social/src/main/java/com/foodhub/social/SocialApplication.java
git commit -m "chore(social): wire post module components"
```

- [ ] **步骤 6：在提交后重新执行最终验证**

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
git status --short --branch
git diff --name-only origin/main...HEAD
```

预期：构建成功、工作区干净，并且相对 `origin/main` 的所有功能变更都位于
`foodhub-social/`。

## 交付说明

提交 Pull Request 时使用以下中文说明：

```markdown
## 实现内容

- 新增帖子发布、分页列表、详情和作者软删除接口
- 新增 Social `post` 表 Flyway 迁移
- 图片 URL 以 JSON 保存，接口返回列表
- 帖子时间以带 `+08:00` 偏移的 ISO-8601 格式返回

## 接口

- `POST /api/posts`：需要 `X-User-Id`
- `GET /api/posts`：公开分页查询
- `GET /api/posts/{postId}`：公开详情查询
- `DELETE /api/posts/{postId}`：需要 `X-User-Id`，仅限作者

## 边界

- 未修改 `foodhub-merchant` 或任何共享模块
- 未查询或调用 Merchant 服务，只保存可选 `merchantId`
- 未实现关注、点赞、评论、收藏和动态流

## 验证

- `./mvnw --batch-mode --no-transfer-progress -pl foodhub-social -am test`
```
