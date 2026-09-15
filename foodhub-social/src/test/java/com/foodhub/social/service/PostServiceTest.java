package com.foodhub.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.InteractionMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private InteractionMapper interactionMapper;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postMapper, interactionMapper, new ObjectMapper());
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
        assertEquals("+08:00", result.publishedAt().getOffset().toString());
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
        PostEntity post = post(5L, 7L);
        post.setLikeCount(3L);
        post.setFavoriteCount(4L);
        when(postMapper.countVisible()).thenReturn(1L);
        when(postMapper.selectVisiblePage(20L, 20)).thenReturn(List.of(post));

        PostPageView result = postService.list(2, 20);

        assertEquals(2, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(1L, result.total());
        assertEquals(1, result.items().size());
        assertEquals(5L, result.items().getFirst().id());
        assertEquals(3L, result.items().getFirst().likeCount());
        assertEquals(4L, result.items().getFirst().favoriteCount());
        assertNull(result.items().getFirst().liked());
        assertNull(result.items().getFirst().favorited());
    }

    @Test
    void listWithViewerAddsInteractionState() {
        when(postMapper.countVisible()).thenReturn(1L);
        when(postMapper.selectVisiblePage(0L, 20)).thenReturn(List.of(post(5L, 7L)));
        when(interactionMapper.existsLike(10L, 5L)).thenReturn(true);
        when(interactionMapper.existsFavorite(10L, 5L)).thenReturn(false);

        PostPageView result = postService.list(1, 20, 10L);

        assertEquals(true, result.items().getFirst().liked());
        assertEquals(false, result.items().getFirst().favorited());
    }

    @Test
    void listReturnsEmptyPageWithoutRunningPageQuery() {
        when(postMapper.countVisible()).thenReturn(0L);

        PostPageView result = postService.list(1, 20);

        assertEquals(0L, result.total());
        assertEquals(List.of(), result.items());
        verify(postMapper, never()).selectVisiblePage(anyLong(), anyInt());
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
    void detailWithViewerAddsInteractionState() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(interactionMapper.existsLike(10L, 5L)).thenReturn(false);
        when(interactionMapper.existsFavorite(10L, 5L)).thenReturn(true);

        PostView result = postService.detail(5L, 10L);

        assertEquals(false, result.liked());
        assertEquals(true, result.favorited());
    }

    @Test
    void detailRejectsMissingOrDeletedPost() {
        when(postMapper.selectVisibleById(5L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.detail(5L));

        assertEquals("POST_NOT_FOUND", exception.getCode());
    }

    @Test
    void detailRejectsInvalidStoredImageJson() {
        PostEntity post = post(5L, 7L);
        post.setImageUrls("not-json");
        when(postMapper.selectVisibleById(5L)).thenReturn(post);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postService.detail(5L));

        assertEquals("POST_DATA_INVALID", exception.getCode());
    }

    @Test
    void deleteAllowsPostAuthor() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(postMapper.softDelete(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(7L),
                any(LocalDateTime.class))).thenReturn(1);

        postService.delete(5L, 7L);

        verify(postMapper).softDelete(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(7L),
                any(LocalDateTime.class));
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
        when(postMapper.softDelete(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(7L),
                any(LocalDateTime.class))).thenReturn(0);

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
        post.setLikeCount(0L);
        post.setFavoriteCount(0L);
        return post;
    }
}
