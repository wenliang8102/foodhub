package com.foodhub.social;

import com.foodhub.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialApplicationContractTest {

    @Test
    void applicationScansSocialMappersAndImportsSharedExceptionHandler() {
        MapperScan mapperScan = SocialApplication.class.getAnnotation(MapperScan.class);
        assertEquals("com.foodhub.social.mapper", mapperScan.value()[0]);

        Import imported = SocialApplication.class.getAnnotation(Import.class);
        assertArrayEquals(new Class<?>[]{GlobalExceptionHandler.class}, imported.value());
    }
}
