package com.foodhub.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.coupon.entity.SeckillActivityEntity;
import com.foodhub.coupon.entity.SeckillOutboxEntity;
import com.foodhub.coupon.entity.SeckillRequestEntity;
import com.foodhub.coupon.mapper.SeckillActivityMapper;
import com.foodhub.coupon.mapper.SeckillOutboxMapper;
import com.foodhub.coupon.mapper.SeckillRequestMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class SeckillReservationPersistence {

    private static final int MAX_OPTIMISTIC_RETRIES = 20;

    private final SeckillActivityMapper activityMapper;
    private final SeckillRequestMapper requestMapper;
    private final SeckillOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public SeckillReservationPersistence(SeckillActivityMapper activityMapper,
                                         SeckillRequestMapper requestMapper,
                                         SeckillOutboxMapper outboxMapper,
                                         ObjectMapper objectMapper) {
        this.activityMapper = activityMapper;
        this.requestMapper = requestMapper;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SeckillRequestEntity reserve(SeckillOrderCreateCommand command,
                                        LocalDateTime now) {
        String requestId = command.requestId();
        long activityId = command.activityId();
        long userId = command.userId();
        if (requestMapper.selectCount(new LambdaQueryWrapper<SeckillRequestEntity>()
                .eq(SeckillRequestEntity::getActivityId, activityId)
                .eq(SeckillRequestEntity::getUserId, userId)) > 0) {
            throw new BusinessException("SECKILL_DUPLICATE", "User has already joined this activity");
        }

        boolean deducted = false;
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_RETRIES && !deducted; attempt++) {
            SeckillActivityEntity latest = activityMapper.selectById(activityId);
            if (latest == null || latest.getRemainingStock() <= 0) {
                throw new BusinessException("SECKILL_SOLD_OUT", "Seckill stock is exhausted");
            }
            deducted = activityMapper.decrementStockOptimistically(
                    activityId, latest.getVersion()) == 1;
        }
        if (!deducted) {
            throw new BusinessException("SECKILL_BUSY", "Seckill is busy, please try again");
        }

        SeckillRequestEntity request = new SeckillRequestEntity();
        request.setRequestId(requestId);
        request.setActivityId(activityId);
        request.setUserId(userId);
        request.setStatus("STOCK_RESERVED");
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("SECKILL_DUPLICATE", "User has already joined this activity");
        }

        SeckillOutboxEntity outbox = new SeckillOutboxEntity();
        outbox.setMessageId(command.messageId());
        outbox.setRequestId(command.requestId());
        outbox.setPayload(writePayload(command));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextRetryAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxMapper.insert(outbox);
        return request;
    }

    private String writePayload(SeckillOrderCreateCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("SECKILL_MESSAGE_INVALID", "Failed to serialize order command");
        }
    }
}
