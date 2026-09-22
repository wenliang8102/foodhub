package com.foodhub.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_outbox")
public class OrderOutboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String aggregateKey;
    private String eventType;
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
