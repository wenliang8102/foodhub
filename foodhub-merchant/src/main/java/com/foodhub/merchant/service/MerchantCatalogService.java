package com.foodhub.merchant.service;

import com.foodhub.merchant.dto.FoodCreateRequest;
import com.foodhub.merchant.dto.FoodUpdateRequest;
import com.foodhub.merchant.dto.MerchantCreateRequest;
import com.foodhub.merchant.dto.MerchantUpdateRequest;
import com.foodhub.merchant.vo.CategoryView;
import com.foodhub.merchant.vo.FoodItemView;
import com.foodhub.merchant.vo.MerchantView;
import com.foodhub.merchant.vo.PageResponse;

import java.util.List;

public interface MerchantCatalogService {

    List<CategoryView> listCategories();

    PageResponse<MerchantView> listMerchants(
            String keyword,
            Long categoryId,
            String sort,
            int page,
            int size);

    MerchantView getMerchant(Long merchantId);

    MerchantView createMerchant(
            String rawUserId,
            String rawRole,
            MerchantCreateRequest request);

    MerchantView updateMerchant(
            String rawUserId,
            String rawRole,
            Long merchantId,
            MerchantUpdateRequest request);

    PageResponse<FoodItemView> listFoods(
            Long merchantId,
            Long categoryId,
            String keyword,
            String sort,
            int page,
            int size);

    FoodItemView getFood(Long foodId);

    FoodItemView createFood(
            String rawUserId,
            String rawRole,
            FoodCreateRequest request);

    FoodItemView updateFood(
            String rawUserId,
            String rawRole,
            Long foodId,
            FoodUpdateRequest request);
}
