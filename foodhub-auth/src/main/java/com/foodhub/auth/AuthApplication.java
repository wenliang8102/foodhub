package com.foodhub.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import com.foodhub.common.security.JwtTokenService;
import com.foodhub.common.web.GlobalExceptionHandler;

@SpringBootApplication
@MapperScan("com.foodhub.auth.mapper")
@Import({JwtTokenService.class, GlobalExceptionHandler.class})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
