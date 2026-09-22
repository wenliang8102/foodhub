package com.foodhub.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.order.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    @Update("""
            UPDATE fh_order
            SET status = 'CANCELLED',
                cancelled_at = #{now},
                updated_at = #{now}
            WHERE id = #{orderId}
              AND status = 'PENDING_PAY'
              AND payment_expires_at <= #{now}
            """)
    int cancelIfExpired(@Param("orderId") long orderId,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE fh_order
            SET status = 'PAID',
                paid_at = #{now},
                updated_at = #{now}
            WHERE id = #{orderId}
              AND user_id = #{userId}
              AND status = 'PENDING_PAY'
              AND payment_expires_at > #{now}
            """)
    int markPaidIfPayable(@Param("orderId") long orderId,
                          @Param("userId") long userId,
                          @Param("now") LocalDateTime now);
}
