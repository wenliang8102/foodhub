package com.foodhub.coupon.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.coupon.dto.CouponCreateRequest;
import com.foodhub.coupon.dto.CouponStatusUpdateRequest;
import com.foodhub.coupon.service.CouponService;
import com.foodhub.coupon.vo.CouponView;
import com.foodhub.coupon.vo.PageResponse;
import com.foodhub.coupon.vo.UserCouponView;
import com.foodhub.coupon.web.CouponRequestIdentity;
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
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    public ApiResponse<CouponView> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CouponCreateRequest request) {
        return ApiResponse.success(couponService.create(identity(userId, role), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<CouponView>> listAvailable(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(couponService.listAvailable(merchantId, page, size));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponView> detail(@PathVariable long couponId) {
        return ApiResponse.success(couponService.detail(couponId));
    }

    @GetMapping("/managed")
    public ApiResponse<PageResponse<CouponView>> managed(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(couponService.managed(identity(userId, role), page, size));
    }

    @PatchMapping("/{couponId}/status")
    public ApiResponse<CouponView> updateStatus(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long couponId,
            @Valid @RequestBody CouponStatusUpdateRequest request) {
        return ApiResponse.success(couponService.updateStatus(
                identity(userId, role), couponId, request.status()));
    }

    @PostMapping("/{couponId}/claim")
    public ApiResponse<UserCouponView> claim(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long couponId) {
        return ApiResponse.success(couponService.claim(identity(userId, role).userId(), couponId));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResponse<UserCouponView>> mine(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(couponService.mine(identity(userId, role).userId(), page, size));
    }

    private CouponRequestIdentity identity(String userId, String role) {
        return CouponRequestIdentity.require(userId, role);
    }
}
