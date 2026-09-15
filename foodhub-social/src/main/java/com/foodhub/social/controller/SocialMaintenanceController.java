package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.social.service.SocialMaintenanceService;
import com.foodhub.social.vo.CounterRepairView;
import com.foodhub.social.web.SocialRequestIdentity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social/maintenance")
public class SocialMaintenanceController {

    private final SocialMaintenanceService maintenanceService;

    public SocialMaintenanceController(SocialMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PatchMapping("/posts/{postId}/counters")
    public ApiResponse<CounterRepairView> repairPostCounters(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        SocialRequestIdentity.requireAdminRole(roleHeader);
        return ApiResponse.success(maintenanceService.repairPostCounters(
                SocialRequestIdentity.requireUserId(userIdHeader), postId));
    }
}
