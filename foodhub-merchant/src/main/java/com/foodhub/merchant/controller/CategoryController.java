package com.foodhub.merchant.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.merchant.service.MerchantCatalogService;
import com.foodhub.merchant.vo.CategoryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final MerchantCatalogService catalogService;

    public CategoryController(MerchantCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<List<CategoryView>> listCategories() {
        return ApiResponse.success(catalogService.listCategories());
    }
}
