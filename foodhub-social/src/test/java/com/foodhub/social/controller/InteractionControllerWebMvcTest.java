package com.foodhub.social.controller;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.service.InteractionService;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = InteractionControllerWebMvcTest.WebTestApplication.class,
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
class InteractionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InteractionService interactionService;

    @Test
    void likeUsesGatewayUserId() throws Exception {
        mockMvc.perform(post("/api/posts/5/likes").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(interactionService).like(7L, 5L);
    }

    @Test
    void unlikeUsesGatewayUserId() throws Exception {
        mockMvc.perform(delete("/api/posts/5/likes").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(interactionService).unlike(7L, 5L);
    }

    @Test
    void favoriteUsesGatewayUserId() throws Exception {
        mockMvc.perform(post("/api/posts/5/favorite").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(interactionService).favorite(7L, 5L);
    }

    @Test
    void unfavoriteUsesGatewayUserId() throws Exception {
        mockMvc.perform(delete("/api/posts/5/favorite").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(interactionService).unfavorite(7L, 5L);
    }

    @Test
    void favoritesReturnsPageWithInteractionFields() throws Exception {
        when(interactionService.favorites(7L, 2, 20))
                .thenReturn(new PostPageView(2, 20, 1, List.of(postView())));

        mockMvc.perform(get("/api/posts/favorites?page=2&pageSize=20")
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(3))
                .andExpect(jsonPath("$.data.items[0].favoriteCount").value(4))
                .andExpect(jsonPath("$.data.items[0].liked").value(true))
                .andExpect(jsonPath("$.data.items[0].favorited").value(true));
    }

    @Test
    void protectedEndpointsRejectMissingUserHeader() throws Exception {
        mockMvc.perform(post("/api/posts/5/likes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(interactionService);
    }

    @Test
    void protectedEndpointsRejectMalformedUserHeader() throws Exception {
        mockMvc.perform(get("/api/posts/favorites").header("X-User-Id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(interactionService);
    }

    @Test
    void favoritesRejectsInvalidPaginationBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/api/posts/favorites?page=0&pageSize=101")
                        .header("X-User-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(interactionService);
    }

    @Test
    void missingPostMapsToNotFound() throws Exception {
        doThrow(new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除"))
                .when(interactionService).like(7L, 5L);

        mockMvc.perform(post("/api/posts/5/likes").header("X-User-Id", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    private PostView postView() {
        return new PostView(
                5L,
                9L,
                "正文",
                List.of("https://example.com/a.jpg"),
                12L,
                OffsetDateTime.parse("2026-09-14T12:00:00+08:00"),
                3L,
                4L,
                0L,
                true,
                true);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
    })
    @Import({InteractionController.class, SocialExceptionHandler.class})
    static class WebTestApplication {
    }
}
