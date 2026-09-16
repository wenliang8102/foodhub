package com.foodhub.merchant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MerchantHttpApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void listsMerchantsWithPagination() throws Exception {
        mockMvc.perform(get("/api/merchants")
                        .param("sort", "sales")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    void getsMerchantDetail() throws Exception {
        mockMvc.perform(get("/api/merchants/{merchantId}", 101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.ownerUserId").value(7))
                .andExpect(jsonPath("$.data.categoryId").value(1))
                .andExpect(jsonPath("$.data.businessStatus").value("OPEN"));
    }

    @Test
    void merchantCanCreateMerchantWithHeaders() throws Exception {
        String body = """
                {
                  "name": "Campus Lunch",
                  "categoryId": 1,
                  "address": "Road 1",
                  "longitude": 116.3000,
                  "latitude": 39.9000
                }
                """;

        mockMvc.perform(post("/api/merchants")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.ownerUserId").value(42))
                .andExpect(jsonPath("$.data.name").value("Campus Lunch"))
                .andExpect(jsonPath("$.data.businessStatus").value("CLOSED"));
    }

    @Test
    void merchantCanUpdateOwnedMerchantWithHeaders() throws Exception {
        String body = """
                {
                  "name": "Updated Shop",
                  "businessStatus": "CLOSED"
                }
                """;

        mockMvc.perform(patch("/api/merchants/{merchantId}", 101)
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.name").value("Updated Shop"))
                .andExpect(jsonPath("$.data.businessStatus").value("CLOSED"));
    }

    @Test
    void merchantCannotUpdateAnotherOwnersMerchant() throws Exception {
        String body = """
                {
                  "name": "Forbidden Update"
                }
                """;

        mockMvc.perform(patch("/api/merchants/{merchantId}", 102)
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("own merchant")));
    }

    @Test
    void listsAndGetsFoods() throws Exception {
        mockMvc.perform(get("/api/foods")
                        .param("merchantId", "101")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        mockMvc.perform(get("/api/foods/{foodId}", 1001))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.merchantId").value(101))
                .andExpect(jsonPath("$.data.onSale").value(true));
    }

    @Test
    void merchantCanCreateFoodForOwnedMerchant() throws Exception {
        String body = """
                {
                  "merchantId": 101,
                  "name": "Smoke Noodles",
                  "price": 21.90
                }
                """;

        mockMvc.perform(post("/api/foods")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.merchantId").value(101))
                .andExpect(jsonPath("$.data.name").value("Smoke Noodles"))
                .andExpect(jsonPath("$.data.onSale").value(true));
    }

    @Test
    void merchantCanUpdateOwnedFoodAndHideItFromPublicDetail() throws Exception {
        String body = """
                {
                  "price": 45.50,
                  "onSale": false
                }
                """;

        mockMvc.perform(patch("/api/foods/{foodId}", 1001)
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.price").value(45.50))
                .andExpect(jsonPath("$.data.onSale").value(false));

        mockMvc.perform(get("/api/foods/{foodId}", 1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
    }

    @Test
    void merchantCannotCreateFoodForAnotherOwner() throws Exception {
        String body = """
                {
                  "merchantId": 102,
                  "name": "Unauthorized Food",
                  "price": 19.90
                }
                """;

        mockMvc.perform(post("/api/foods")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("own merchant")));
    }

    @Test
    void createMerchantValidatesRequiredFields() throws Exception {
        String body = """
                {
                  "name": "",
                  "categoryId": 1,
                  "address": "Road 1",
                  "longitude": 116.3000,
                  "latitude": 39.9000
                }
                """;

        mockMvc.perform(post("/api/merchants")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("name")));
    }
}
