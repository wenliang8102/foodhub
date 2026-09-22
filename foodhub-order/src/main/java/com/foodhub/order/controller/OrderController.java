package com.foodhub.order.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.order.service.OrderQueryService;
import com.foodhub.order.service.OrderPaymentService;
import com.foodhub.order.vo.OrderView;
import com.foodhub.order.vo.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService queryService;
    private final OrderPaymentService paymentService;

    public OrderController(OrderQueryService queryService,
                           OrderPaymentService paymentService) {
        this.queryService = queryService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderView>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(queryService.list(requireUserId(userId), page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderView> detail(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String orderNo) {
        return ApiResponse.success(queryService.detail(requireUserId(userId), orderNo));
    }

    @PostMapping("/{orderNo}/pay")
    public ApiResponse<OrderView> pay(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String orderNo) {
        long actorId = requireUserId(userId);
        paymentService.pay(actorId, orderNo);
        return ApiResponse.success(queryService.detail(actorId, orderNo));
    }

    private long requireUserId(String value) {
        try {
            long userId = Long.parseLong(value == null ? "" : value.trim());
            if (userId <= 0) throw new NumberFormatException();
            return userId;
        } catch (NumberFormatException exception) {
            throw new BusinessException("UNAUTHORIZED", "X-User-Id header must be a positive number");
        }
    }
}
