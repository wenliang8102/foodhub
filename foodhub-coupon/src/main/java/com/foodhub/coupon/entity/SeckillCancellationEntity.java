package com.foodhub.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_cancellation")
public class SeckillCancellationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String orderNo;
    private String requestId;
    private Long activityId;
    private Long userId;
    private Integer quantity;
    private LocalDateTime processedAt;
}
