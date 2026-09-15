package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.UpdatePostVisibilityRequest;
import com.foodhub.social.entity.ModerationLogEntity;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.ModerationLogMapper;
import com.foodhub.social.mapper.PostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private ModerationLogMapper logMapper;

    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(postMapper, logMapper);
    }

    @Test
    void hiddenVisiblePostWritesAuditLog() {
        PostEntity post = post("VISIBLE");
        when(postMapper.selectAnyById(5L)).thenReturn(post);
        when(postMapper.updateVisibility(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq("VISIBLE"),
                org.mockito.ArgumentMatchers.eq("HIDDEN"),
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("违规"),
                any(LocalDateTime.class))).thenReturn(1);

        moderationService.updateVisibility(
                99L, 5L, new UpdatePostVisibilityRequest("hidden", "  违规  "));

        verify(logMapper).insertLog(any(ModerationLogEntity.class));
    }

    @Test
    void deletedPostCannotBeRestored() {
        when(postMapper.selectAnyById(5L)).thenReturn(post("DELETED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> moderationService.updateVisibility(
                        99L, 5L, new UpdatePostVisibilityRequest("VISIBLE", "恢复")));

        assertEquals("POST_NOT_FOUND", exception.getCode());
        verify(logMapper, never()).insertLog(any(ModerationLogEntity.class));
    }

    @Test
    void invalidStatusIsValidationError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> moderationService.updateVisibility(
                        99L, 5L, new UpdatePostVisibilityRequest("DELETED", "原因")));

        assertEquals("VALIDATION_ERROR", exception.getCode());
    }

    private PostEntity post(String status) {
        PostEntity post = new PostEntity();
        post.setId(5L);
        post.setStatus(status);
        return post;
    }
}
