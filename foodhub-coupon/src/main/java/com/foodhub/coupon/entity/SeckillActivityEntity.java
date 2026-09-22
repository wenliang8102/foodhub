package com.foodhub.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seckill_activity")
public class SeckillActivityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private Long creatorUserId;
    private String targetType;
    private Long targetId;
    private String title;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer remainingStock;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
