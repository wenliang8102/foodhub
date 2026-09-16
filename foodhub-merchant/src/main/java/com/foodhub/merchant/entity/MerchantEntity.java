package com.foodhub.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fh_merchant")
public class MerchantEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long categoryId;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal rating;
    private Long salesCount;
    private String businessStatus;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
