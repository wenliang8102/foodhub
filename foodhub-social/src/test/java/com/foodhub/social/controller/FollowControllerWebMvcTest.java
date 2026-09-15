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
