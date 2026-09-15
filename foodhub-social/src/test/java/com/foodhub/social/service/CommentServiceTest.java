package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreateCommentRequest;
import com.foodhub.social.entity.CommentEntity;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.CommentMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.CommentPageView;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private CommentMapper commentMapper;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(postMapper, commentMapper);
    }

    @Test
    void createTrimsContentAndIncrementsCounter() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post());

        commentService.create(7L, 5L, new CreateCommentRequest("  好吃  "));

        verify(commentMapper).insertVisible(any(CommentEntity.class));
        verify(commentMapper).incrementCommentCount(5L);
    }

    @Test
    void createRejectsBlankContent() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commentService.create(7L, 5L, new CreateCommentRequest(" ")));

        assertEquals("VALIDATION_ERROR", exception.getCode());
        verify(commentMapper, never()).insertVisible(any(CommentEntity.class));
    }

    @Test
    void listReturnsVisibleCommentsAndShortCircuitsEmptyPage() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post());
        when(commentMapper.countVisibleByPostId(5L)).thenReturn(0L);

        CommentPageView result = commentService.list(5L, 1, 20);

        assertEquals(0L, result.total());
        assertEquals(List.of(), result.items());
        verify(commentMapper, never()).selectVisiblePage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void deleteAllowsAuthorAndDecrementsCounter() {
        CommentEntity comment = comment(9L, 7L, "VISIBLE");
        when(postMapper.selectVisibleById(5L)).thenReturn(post());
        when(commentMapper.selectByPostAndId(5L, 9L)).thenReturn(comment);
        when(commentMapper.softDeleteVisible(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(7L),
                any(LocalDateTime.class))).thenReturn(1);

        commentService.delete(7L, 5L, 9L);

        verify(commentMapper).decrementCommentCount(5L);
    }

    @Test
    void deleteRejectsAnotherUser() {
        when(postMapper.selectVisibleById(5L)).thenReturn(post());
        when(commentMapper.selectByPostAndId(5L, 9L)).thenReturn(comment(9L, 8L, "VISIBLE"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commentService.delete(7L, 5L, 9L));

        assertEquals("COMMENT_FORBIDDEN", exception.getCode());
    }

    private PostEntity post() {
        PostEntity post = new PostEntity();
        post.setId(5L);
        post.setStatus("VISIBLE");
        return post;
    }

    private CommentEntity comment(long id, long authorId, String status) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setPostId(5L);
        comment.setAuthorId(authorId);
        comment.setContent("好吃");
        comment.setStatus(status);
        comment.setCreatedAt(LocalDateTime.of(2026, 9, 15, 12, 0));
        return comment;
    }
}
