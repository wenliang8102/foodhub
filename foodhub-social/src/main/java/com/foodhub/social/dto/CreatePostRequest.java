package com.foodhub.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "帖子正文不能为空")
        @Size(max = 2000, message = "帖子正文不能超过 2000 个字符")
        String content,

        @Size(max = 9, message = "帖子图片不能超过 9 张")
        List<@NotBlank(message = "图片地址不能为空")
                @Size(max = 2048, message = "图片地址不能超过 2048 个字符") String> imageUrls,

        @Positive(message = "merchantId 必须为正数")
        Long merchantId) {
}
