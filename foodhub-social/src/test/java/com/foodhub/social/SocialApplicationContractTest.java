package com.foodhub.social;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SocialApplicationContractTest {

    @Test
    void applicationScansSocialMappersWithoutSharedExceptionHandlerImport() {
        MapperScan mapperScan = SocialApplication.class.getAnnotation(MapperScan.class);
        assertEquals("com.foodhub.social.mapper", mapperScan.value()[0]);
        assertNull(SocialApplication.class.getAnnotation(Import.class));
    }
}
