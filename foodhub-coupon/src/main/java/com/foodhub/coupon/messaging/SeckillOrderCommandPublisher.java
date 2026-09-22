package com.foodhub.coupon.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.messaging.RabbitMqContracts;
import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.coupon.entity.SeckillOutboxEntity;
import com.foodhub.coupon.mapper.SeckillOutboxMapper;
import com.foodhub.coupon.service.SeckillOutboxStateService;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillOrderCommandPublisher {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final SeckillOutboxMapper outboxMapper;
    private final SeckillOutboxStateService stateService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public SeckillOrderCommandPublisher(SeckillOutboxMapper outboxMapper,
                                        SeckillOutboxStateService stateService,
                                        RabbitTemplate rabbitTemplate,
                                        ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.stateService = stateService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean publishRequest(String requestId) {
        SeckillOutboxEntity outbox = outboxMapper.selectOne(
                new LambdaQueryWrapper<SeckillOutboxEntity>()
                        .eq(SeckillOutboxEntity::getRequestId, requestId));
        return outbox != null && publish(outbox);
    }

    @Scheduled(fixedDelayString = "${foodhub.seckill.outbox-delay-ms:3000}")
    public void retryPending() {
        LocalDateTime now = now();
        List<SeckillOutboxEntity> pending = outboxMapper.selectList(
                new LambdaQueryWrapper<SeckillOutboxEntity>()
                        .eq(SeckillOutboxEntity::getStatus, "PENDING")
                        .le(SeckillOutboxEntity::getNextRetryAt, now)
                        .orderByAsc(SeckillOutboxEntity::getId)
                        .last("LIMIT 50"));
        pending.forEach(this::publish);
    }

    private boolean publish(SeckillOutboxEntity outbox) {
        try {
            SeckillOrderCreateCommand command = objectMapper.readValue(
                    outbox.getPayload(), SeckillOrderCreateCommand.class);
            CorrelationData correlation = new CorrelationData(outbox.getMessageId());
            rabbitTemplate.convertAndSend(
                    RabbitMqContracts.BUSINESS_EXCHANGE,
                    RabbitMqContracts.SECKILL_ORDER_CREATE_ROUTING_KEY,
                    command,
                    correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                markFailed(outbox, confirm.getReason() == null ? "Message was returned" : confirm.getReason());
                return false;
            }
            stateService.markSent(outbox.getId(), outbox.getRequestId(), now());
            return true;
        } catch (Exception exception) {
            markFailed(outbox, message(exception));
            return false;
        }
    }

    private void markFailed(SeckillOutboxEntity outbox, String error) {
        LocalDateTime now = now();
        int attempts = outbox.getAttempts() == null ? 0 : outbox.getAttempts();
        long delaySeconds = Math.min(60, 1L << Math.min(attempts, 6));
        outboxMapper.markFailed(
                outbox.getId(), now.plusSeconds(delaySeconds), truncate(error), now);
    }

    private String message(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return "Invalid outbox payload";
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String truncate(String value) {
        String safe = value == null ? "Unknown publish failure" : value;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(DATABASE_ZONE);
    }
}
