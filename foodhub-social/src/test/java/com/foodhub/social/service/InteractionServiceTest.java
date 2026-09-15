package com.foodhub.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.InteractionMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.PostPageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private InteractionMapper interactionMapper;

    private InteractionService interactionService;

    @BeforeEach
    void setUp() {
        interactionService = new InteractionService(
                postMapper, interactionMapper, new ObjectMapper());
    }

    @Test
    void firstLikeCreatesFactAndIncrementsCounter() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(interactionMapper.insertLikeIfAbsent(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(5L),
                any(LocalDateTime.class))).thenReturn(1);

        interactionService.like(10L, 5L);

        verify(interactionMapper).incrementLikeCount(5L);
    }

    @Test
    void repeatedLikeKeepsCounterUnchanged() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(interactionMapper.insertLikeIfAbsent(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(5L),
                any(LocalDateTime.class))).thenReturn(0);

        interactionService.like(10L, 5L);

        verify(interactionMapper, never()).incrementLikeCount(5L);
    }

    @Test
    void unlikeIsIdempotentAndOnlyDecrementsExistingFact() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(interactionMapper.deleteLike(10L, 5L)).thenReturn(1);

        interactionService.unlike(10L, 5L);

        verify(interactionMapper).decrementLikeCount(5L);

        when(interactionMapper.deleteLike(10L, 5L)).thenReturn(0);

        interactionService.unlike(10L, 5L);

        verify(interactionMapper).decrementLikeCount(5L);
    }

    @Test
    void firstFavoriteCreatesFactAndIncrementsCounter() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(interactionMapper.insertFavoriteIfAbsent(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(5L),
                any(LocalDateTime.class))).thenReturn(1);

        interactionService.favorite(10L, 5L);

        verify(interactionMapper).incrementFavoriteCount(5L);
    }

    @Test
    void unfavoriteIsIdempotentAndOnlyDecrementsExistingFact() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post(5L, 7L));
        when(interactionMapper.deleteFavorite(10L, 5L)).thenReturn(0);

        interactionService.unfavorite(10L, 5L);

        verify(interactionMapper, never()).decrementFavoriteCount(5L);
    }

    @Test
    void writingInteractionRejectsMissingOrDeletedPost() {
        when(postMapper.selectVisibleById(5L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interactionService.like(10L, 5L));

        assertEquals("POST_NOT_FOUND", exception.getCode());
        verify(interactionMapper, never()).insertLikeIfAbsent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(LocalDateTime.class));
    }

    @Test
    void writingInteractionRejectsInvalidUserId() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interactionService.favorite(0L, 5L));

        assertEquals("INVALID_USER_ID", exception.getCode());
        verify(postMapper, never()).selectVisibleById(
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void favoritePageReturnsVisibleFavoritesWithCountersAndViewerState() {
        PostEntity post = post(5L, 7L);
        post.setLikeCount(3L);
        post.setFavoriteCount(4L);
        when(interactionMapper.countVisibleFavorites(10L)).thenReturn(1L);
        when(interactionMapper.selectVisibleFavoritePage(10L, 20L, 20))
                .thenReturn(List.of(post));
        when(interactionMapper.existsLike(10L, 5L)).thenReturn(true);

        PostPageView result = interactionService.favorites(10L, 2, 20);

        assertEquals(2, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(1L, result.total());
        assertEquals(3L, result.items().getFirst().likeCount());
        assertEquals(4L, result.items().getFirst().favoriteCount());
        assertEquals(true, result.items().getFirst().liked());
        assertEquals(true, result.items().getFirst().favorited());
    }

    @Test
    void favoritePageShortCircuitsWhenEmpty() {
        when(interactionMapper.countVisibleFavorites(10L)).thenReturn(0L);

        PostPageView result = interactionService.favorites(10L, 1, 20);

        assertEquals(0L, result.total());
        assertEquals(List.of(), result.items());
        verify(interactionMapper, never()).selectVisibleFavoritePage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void favoritePageRejectsInvalidPagination() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interactionService.favorites(10L, 0, 101));

        assertEquals("VALIDATION_ERROR", exception.getCode());
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
