package com.foodhub.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false"
        })
class GatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
