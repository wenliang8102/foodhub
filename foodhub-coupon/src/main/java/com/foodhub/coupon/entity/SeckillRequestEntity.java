package com.foodhub.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_request")
public class SeckillRequestEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long activityId;
    private Long userId;
    private String status;
    private String orderNo;
    private String failureCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
