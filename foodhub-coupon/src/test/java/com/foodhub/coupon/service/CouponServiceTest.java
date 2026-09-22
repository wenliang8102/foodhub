package com.foodhub.coupon.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.coupon.client.MerchantClient;
import com.foodhub.coupon.dto.CouponCreateRequest;
import com.foodhub.coupon.entity.CouponEntity;
import com.foodhub.coupon.entity.UserCouponEntity;
import com.foodhub.coupon.mapper.CouponMapper;
import com.foodhub.coupon.mapper.UserCouponMapper;
import com.foodhub.coupon.vo.UserCouponView;
import com.foodhub.coupon.web.CouponRequestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    private CouponMapper couponMapper;
    private UserCouponMapper userCouponMapper;
    private MerchantClient merchantClient;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponMapper = mock(CouponMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        merchantClient = mock(MerchantClient.class);
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
        couponService = new CouponService(couponMapper, userCouponMapper, merchantClient, clock);
    }

    @Test
    void rejectsCouponWhenThresholdIsLowerThanFaceValue() {
        CouponCreateRequest request = new CouponCreateRequest(
                101L, "满减券", new BigDecimal("20.00"), new BigDecimal("10.00"), 100,
                atOffset(-1), atOffset(24));

        assertThatThrownBy(() -> couponService.create(
                new CouponRequestIdentity(7L, "MERCHANT"), request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("COUPON_AMOUNT_INVALID");

        verify(couponMapper, never()).insert(any(CouponEntity.class));
    }

    @Test
    void rejectsDuplicateClaimBeforeStockDeduction() {
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> couponService.claim(9L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("COUPON_ALREADY_CLAIMED");

        verify(couponMapper, never()).decrementStockIfClaimable(anyLong(), any());
    }

    @Test
    void claimsCouponAfterAtomicStockDeduction() {
        CouponEntity coupon = availableCoupon();
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(couponMapper.decrementStockIfClaimable(anyLong(), any())).thenReturn(1);
        when(userCouponMapper.insert(any(UserCouponEntity.class))).thenAnswer(invocation -> {
            UserCouponEntity entity = invocation.getArgument(0);
            entity.setId(501L);
            return 1;
        });
        coupon.setRemainingStock(9);
        when(couponMapper.selectById(1001L)).thenReturn(coupon);

        UserCouponView result = couponService.claim(9L, 1001L);

        assertThat(result.id()).isEqualTo(501L);
        assertThat(result.status()).isEqualTo("UNUSED");
        assertThat(result.coupon().remainingStock()).isEqualTo(9);
        verify(userCouponMapper).insert(any(UserCouponEntity.class));
    }

    @Test
    void reportsSoldOutWithoutCreatingUserCoupon() {
        CouponEntity coupon = availableCoupon();
        coupon.setRemainingStock(0);
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(couponMapper.decrementStockIfClaimable(anyLong(), any())).thenReturn(0);
        when(couponMapper.selectById(1001L)).thenReturn(coupon);

        assertThatThrownBy(() -> couponService.claim(9L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo("COUPON_SOLD_OUT");

        verify(userCouponMapper, never()).insert(any(UserCouponEntity.class));
    }

    private CouponEntity availableCoupon() {
        CouponEntity coupon = new CouponEntity();
        coupon.setId(1001L);
        coupon.setMerchantId(101L);
        coupon.setCreatorUserId(7L);
        coupon.setName("满减券");
        coupon.setFaceValue(new BigDecimal("10.00"));
        coupon.setMinSpend(new BigDecimal("50.00"));
        coupon.setTotalStock(10);
        coupon.setRemainingStock(10);
        coupon.setValidFrom(java.time.LocalDateTime.ofInstant(NOW.minusSeconds(3600),
                ZoneId.of("Asia/Shanghai")));
        coupon.setValidUntil(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(3600),
                ZoneId.of("Asia/Shanghai")));
        coupon.setStatus("ACTIVE");
        return coupon;
    }

    private OffsetDateTime atOffset(long hours) {
        return OffsetDateTime.ofInstant(NOW.plusSeconds(hours * 3600), ZoneOffset.ofHours(8));
    }
}
