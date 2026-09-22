package com.foodhub.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_consumed_message")
public class ConsumedMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String requestId;
    private LocalDateTime consumedAt;
}
