package com.foodhub.merchant.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.merchant.dto.MerchantCreateRequest;
import com.foodhub.merchant.dto.MerchantUpdateRequest;
import com.foodhub.merchant.service.MerchantCatalogService;
import com.foodhub.merchant.vo.MerchantView;
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
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantCatalogService catalogService;

    public MerchantController(MerchantCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MerchantView>> listMerchants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(catalogService.listMerchants(keyword, categoryId, sort, page, size));
    }

    @GetMapping("/{merchantId}")
    public ApiResponse<MerchantView> getMerchant(@PathVariable Long merchantId) {
        return ApiResponse.success(catalogService.getMerchant(merchantId));
    }

    @PostMapping
    public ApiResponse<MerchantView> createMerchant(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody MerchantCreateRequest request) {
        return ApiResponse.success(catalogService.createMerchant(userId, role, request));
    }

    @PatchMapping("/{merchantId}")
    public ApiResponse<MerchantView> updateMerchant(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long merchantId,
            @Valid @RequestBody MerchantUpdateRequest request) {
        return ApiResponse.success(catalogService.updateMerchant(userId, role, merchantId, request));
    }
}
