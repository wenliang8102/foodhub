package com.foodhub.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fh_food_item")
public class FoodItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String name;
    private BigDecimal price;
    private String imageUrl;
    private Long salesCount;
    private Boolean onSale;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
