package com.foodhub.coupon.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.coupon.client.MerchantClient;
import com.foodhub.coupon.entity.SeckillActivityEntity;
import com.foodhub.coupon.entity.SeckillRequestEntity;
import com.foodhub.coupon.mapper.CouponMapper;
import com.foodhub.coupon.mapper.SeckillActivityMapper;
import com.foodhub.coupon.mapper.SeckillRequestMapper;
import com.foodhub.coupon.messaging.SeckillOrderCommandPublisher;
import com.foodhub.coupon.vo.SeckillResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private SeckillActivityMapper activityMapper;
    private SeckillRedisService redisService;
    private SeckillReservationPersistence reservationPersistence;
    private SeckillOrderCommandPublisher commandPublisher;
    private SeckillService service;

    @BeforeEach
    void setUp() {
        activityMapper = mock(SeckillActivityMapper.class);
        SeckillRequestMapper requestMapper = mock(SeckillRequestMapper.class);
        CouponMapper couponMapper = mock(CouponMapper.class);
        MerchantClient merchantClient = mock(MerchantClient.class);
        redisService = mock(SeckillRedisService.class);
        reservationPersistence = mock(SeckillReservationPersistence.class);
        commandPublisher = mock(SeckillOrderCommandPublisher.class);
        service = new SeckillService(
                activityMapper, requestMapper, couponMapper, merchantClient, redisService,
                reservationPersistence, commandPublisher,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void rejectsPathBeforeActivityStarts() {
        when(activityMapper.selectById(1001L)).thenReturn(activityStartingLater());

        assertThatThrownBy(() -> service.issuePath(9L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("SECKILL_NOT_STARTED");

        verify(redisService, never()).savePath(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void rejectsPathWhenStockWasNotPreheated() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity());
        when(redisService.isReady(1001L)).thenReturn(false);

        assertThatThrownBy(() -> service.issuePath(9L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("SECKILL_NOT_READY");
    }

    @Test
    void returnsStockReservedAfterRedisAndDatabaseSucceed() {
        SeckillActivityEntity activity = activeActivity();
        when(activityMapper.selectById(1001L)).thenReturn(activity);
        when(redisService.reserve(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(0L);
        when(reservationPersistence.reserve(any(), any()))
                .thenAnswer(invocation -> {
                    SeckillRequestEntity request = new SeckillRequestEntity();
                    com.foodhub.common.messaging.SeckillOrderCreateCommand command =
                            invocation.getArgument(0);
                    request.setRequestId(command.requestId());
                    request.setActivityId(1001L);
                    request.setUserId(9L);
                    request.setStatus("STOCK_RESERVED");
                    request.setCreatedAt(java.time.LocalDateTime.ofInstant(
                            NOW, ZoneId.of("Asia/Shanghai")));
                    return request;
                });
        when(commandPublisher.publishRequest(anyString())).thenReturn(true);

        SeckillResultView result = service.submit(9L, 1001L, "valid-path");

        assertThat(result.status()).isEqualTo("MESSAGE_SENT");
        assertThat(result.requestId()).startsWith("SK");
        verify(redisService, never()).rollback(anyLong(), anyLong(), anyString());
    }

    @Test
    void restoresRedisReservationWhenDatabasePersistenceFails() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity());
        when(redisService.reserve(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(0L);
        when(reservationPersistence.reserve(any(), any()))
                .thenThrow(new BusinessException("SECKILL_BUSY", "busy"));

        assertThatThrownBy(() -> service.submit(9L, 1001L, "valid-path"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("SECKILL_BUSY");

        verify(redisService).rollback(org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(9L), anyString());
    }

    private SeckillActivityEntity activeActivity() {
        SeckillActivityEntity activity = baseActivity();
        activity.setStartAt(java.time.LocalDateTime.ofInstant(
                NOW.minusSeconds(60), ZoneId.of("Asia/Shanghai")));
        activity.setEndAt(java.time.LocalDateTime.ofInstant(
                NOW.plusSeconds(600), ZoneId.of("Asia/Shanghai")));
        return activity;
    }

    private SeckillActivityEntity activityStartingLater() {
        SeckillActivityEntity activity = baseActivity();
        activity.setStartAt(java.time.LocalDateTime.ofInstant(
                NOW.plusSeconds(60), ZoneId.of("Asia/Shanghai")));
        activity.setEndAt(java.time.LocalDateTime.ofInstant(
                NOW.plusSeconds(600), ZoneId.of("Asia/Shanghai")));
        return activity;
    }

    private SeckillActivityEntity baseActivity() {
        SeckillActivityEntity activity = new SeckillActivityEntity();
        activity.setId(1001L);
        activity.setMerchantId(101L);
        activity.setCreatorUserId(7L);
        activity.setTargetType("FOOD_ITEM");
        activity.setTargetId(2001L);
        activity.setTitle("限时秒杀");
        activity.setSeckillPrice(new BigDecimal("19.90"));
        activity.setTotalStock(100);
        activity.setRemainingStock(100);
        activity.setStatus("ACTIVE");
        activity.setVersion(0);
        return activity;
    }
}
