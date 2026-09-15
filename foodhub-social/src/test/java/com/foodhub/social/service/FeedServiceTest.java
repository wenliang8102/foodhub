package com.foodhub.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.InteractionMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.FeedPageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private InteractionMapper interactionMapper;

    @Mock
    private SocialMetrics metrics;

    private FeedService feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedService(
                postMapper,
                new SocialPostViewAssembler(new ObjectMapper(), interactionMapper),
                metrics);
    }

    @Test
    void firstPageReturnsItemsAndNextCursorWhenMoreRowsExist() {
        when(postMapper.selectFeedFirstPage(7L, 3))
                .thenReturn(List.of(post(5L), post(4L), post(3L)));

        FeedPageView result = feedService.feed(7L, 2, null, null);

        assertEquals(2, result.items().size());
        assertEquals(4L, result.nextBeforePostId());
    }

    @Test
    void cursorMustBeProvidedAsPair() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> feedService.feed(7L, 20, OffsetDateTime.parse("2026-09-15T12:00:00+08:00"), null));

        assertEquals("VALIDATION_ERROR", exception.getCode());
    }

    @Test
    void emptyPageHasNoNextCursor() {
        when(postMapper.selectFeedFirstPage(7L, 21)).thenReturn(List.of());

        FeedPageView result = feedService.feed(7L, 20, null, null);

        assertNull(result.nextBeforePostId());
        assertEquals(List.of(), result.items());
    }

    private PostEntity post(long id) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(9L);
        post.setContent("正文");
        post.setImageUrls("[]");
        post.setStatus("VISIBLE");
        post.setPublishedAt(LocalDateTime.of(2026, 9, 15, 12, 0).minusMinutes(id));
        post.setUpdatedAt(post.getPublishedAt());
        post.setLikeCount(0L);
        post.setFavoriteCount(0L);
        post.setCommentCount(0L);
        return post;
    }
}
