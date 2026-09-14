package com.foodhub.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.auth.dto.LoginRequest;
import com.foodhub.auth.dto.RegisterRequest;
import com.foodhub.auth.service.AuthService;
import com.foodhub.auth.vo.AuthResponse;
import com.foodhub.auth.vo.UserProfile;
import com.foodhub.common.security.JwtPrincipal;
import com.foodhub.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = AuthControllerWebMvcTest.WebTestApplication.class,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "foodhub.security.jwt.secret=test-secret-with-at-least-32-characters-long",
        "foodhub.security.jwt.ttl=PT2H"
})
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void registerReturnsTokenAndProfile() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "Password123!", null, "alice@example.com");
        AuthResponse response = new AuthResponse("jwt-token",
                new UserProfile(1L, "alice", null, "alice@example.com", "USER"));
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    void invalidRegisterRequestIsRejectedBeforeServiceCall() throws Exception {
        RegisterRequest request = new RegisterRequest("a", "short", null, "bad-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void protectedCurrentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCurrentUserUsesPrincipalIdentity() throws Exception {
        UserProfile profile = new UserProfile(7L, "alice", null, null, "USER");
        when(authService.currentUser(7L)).thenReturn(profile);

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                new JwtPrincipal(7L, "alice", "USER"), null, java.util.List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.username").value("alice"));
        verify(authService).currentUser(7L);
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        LoginRequest request = new LoginRequest("alice", "Password123!");
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-token", new UserProfile(1L, "alice", null, null, "USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            (request, response, exception) -> response.sendError(401)))
                    .build();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
    })
    @Import({AuthController.class, UserController.class, AuthControllerWebMvcTest.TestSecurityConfig.class})
    static class WebTestApplication {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
