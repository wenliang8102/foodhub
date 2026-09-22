package com.foodhub.coupon.client;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.coupon.web.CouponRequestIdentity;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

@Component
public class MerchantClient {

    private final RestClient restClient;

    public MerchantClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl("http://foodhub-merchant")
                .build();
    }

    public void requireManageableMerchant(CouponRequestIdentity actor, long merchantId) {
        final ApiResponse<MerchantSummary> response;
        try {
            response = restClient.get()
                    .uri("/api/merchants/{merchantId}", merchantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (RestClientException exception) {
            throw new BusinessException(
                    "MERCHANT_UNAVAILABLE", "Merchant could not be verified");
        }
        if (response == null || response.data() == null) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "Merchant does not exist");
        }
        if (!actor.isAdmin() && !Objects.equals(response.data().ownerUserId(), actor.userId())) {
            throw new BusinessException(
                    "COUPON_FORBIDDEN", "Merchant can only create coupons for its own store");
        }
    }

    public void requireFoodBelongsToMerchant(long foodId, long merchantId) {
        final ApiResponse<FoodSummary> response;
        try {
            response = restClient.get()
                    .uri("/api/foods/{foodId}", foodId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (RestClientException exception) {
            throw new BusinessException("FOOD_UNAVAILABLE", "Food item could not be verified");
        }
        if (response == null || response.data() == null
                || !Objects.equals(response.data().merchantId(), merchantId)) {
            throw new BusinessException(
                    "SECKILL_TARGET_INVALID", "Food item does not belong to the selected merchant");
        }
    }

    private record MerchantSummary(Long id, Long ownerUserId) {
    }

    private record FoodSummary(Long id, Long merchantId) {
    }
}
