package com.foodhub.merchant.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.merchant.dto.FoodCreateRequest;
import com.foodhub.merchant.dto.FoodUpdateRequest;
import com.foodhub.merchant.service.MerchantCatalogService;
import com.foodhub.merchant.vo.FoodItemView;
import com.foodhub.merchant.vo.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final MerchantCatalogService catalogService;

    public FoodController(MerchantCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FoodItemView>> listFoods(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "sales") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(catalogService.listFoods(merchantId, categoryId, keyword, sort, page, size));
    }

    @GetMapping("/{foodId}")
    public ApiResponse<FoodItemView> getFood(@PathVariable Long foodId) {
        return ApiResponse.success(catalogService.getFood(foodId));
    }

    @PostMapping
    public ApiResponse<FoodItemView> createFood(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody FoodCreateRequest request) {
        return ApiResponse.success(catalogService.createFood(userId, role, request));
    }

    @PatchMapping("/{foodId}")
    public ApiResponse<FoodItemView> updateFood(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long foodId,
            @Valid @RequestBody FoodUpdateRequest request) {
        return ApiResponse.success(catalogService.updateFood(userId, role, foodId, request));
    }
}
