package com.foodhub.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.coupon.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CouponMapper extends BaseMapper<CouponEntity> {

    @Update("""
            UPDATE coupon
            SET remaining_stock = remaining_stock - 1,
                updated_at = #{now}
            WHERE id = #{couponId}
              AND status = 'ACTIVE'
              AND remaining_stock > 0
              AND valid_from <= #{now}
              AND valid_until > #{now}
            """)
    int decrementStockIfClaimable(@Param("couponId") long couponId,
                                  @Param("now") LocalDateTime now);
}
