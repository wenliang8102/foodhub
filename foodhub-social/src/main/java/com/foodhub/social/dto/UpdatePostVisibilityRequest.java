package com.foodhub.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePostVisibilityRequest(
        @NotNull(message = "目标状态不能为空")
        String status,
        @NotBlank(message = "治理原因不能为空")
        @Size(max = 500, message = "治理原因最多 500 个字符")
        String reason) {
}
