package com.foodhub.social;

import com.foodhub.common.web.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("com.foodhub.social.mapper")
@Import(GlobalExceptionHandler.class)
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }
}
