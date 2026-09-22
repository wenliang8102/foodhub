package com.foodhub.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class CouponEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private Long creatorUserId;
    private String name;
    private BigDecimal faceValue;
    private BigDecimal minSpend;
    private Integer totalStock;
    private Integer remainingStock;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
