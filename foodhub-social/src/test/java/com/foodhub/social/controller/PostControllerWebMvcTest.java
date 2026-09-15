package com.foodhub.social.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.service.PostService;
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
import org.springframework.http.MediaType;
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
        classes = PostControllerWebMvcTest.WebTestApplication.class,
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

    @Test
    void deleteRejectsMissingUserHeader() throws Exception {
        mockMvc.perform(delete("/api/posts/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

        verifyNoInteractions(postService);
    }

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
    @Import({PostController.class, SocialExceptionHandler.class})
    static class WebTestApplication {
    }
}
