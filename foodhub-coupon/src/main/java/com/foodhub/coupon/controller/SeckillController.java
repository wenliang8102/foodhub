package com.foodhub.coupon.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.coupon.dto.SeckillActivityCreateRequest;
import com.foodhub.coupon.dto.SeckillSubmitRequest;
import com.foodhub.coupon.service.SeckillService;
import com.foodhub.coupon.vo.PageResponse;
import com.foodhub.coupon.vo.SeckillActivityView;
import com.foodhub.coupon.vo.SeckillPathView;
import com.foodhub.coupon.vo.SeckillResultView;
import com.foodhub.coupon.web.CouponRequestIdentity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/activities")
    public ApiResponse<SeckillActivityView> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody SeckillActivityCreateRequest request) {
        return ApiResponse.success(seckillService.create(identity(userId, role), request));
    }

    @GetMapping("/activities")
    public ApiResponse<PageResponse<SeckillActivityView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(seckillService.list(page, size));
    }

    @GetMapping("/activities/{activityId}")
    public ApiResponse<SeckillActivityView> detail(@PathVariable long activityId) {
        return ApiResponse.success(seckillService.detail(activityId));
    }

    @PostMapping("/activities/{activityId}/preheat")
    public ApiResponse<SeckillActivityView> preheat(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long activityId) {
        return ApiResponse.success(seckillService.preheat(identity(userId, role), activityId));
    }

    @PostMapping("/activities/{activityId}/path")
    public ApiResponse<SeckillPathView> issuePath(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long activityId) {
        return ApiResponse.success(seckillService.issuePath(
                identity(userId, role).userId(), activityId));
    }

    @PostMapping("/activities/{activityId}/submit")
    public ApiResponse<SeckillResultView> submit(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long activityId,
            @Valid @RequestBody SeckillSubmitRequest request) {
        return ApiResponse.success(seckillService.submit(
                identity(userId, role).userId(), activityId, request.path()));
    }

    @GetMapping("/activities/{activityId}/result")
    public ApiResponse<SeckillResultView> result(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long activityId) {
        return ApiResponse.success(seckillService.result(
                identity(userId, role).userId(), activityId));
    }

    private CouponRequestIdentity identity(String userId, String role) {
        return CouponRequestIdentity.require(userId, role);
    }
}
