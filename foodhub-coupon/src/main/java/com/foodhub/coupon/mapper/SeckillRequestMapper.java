package com.foodhub.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.coupon.entity.SeckillRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SeckillRequestMapper extends BaseMapper<SeckillRequestEntity> {

    @Update("""
            UPDATE seckill_request
            SET status = 'MESSAGE_SENT', updated_at = #{now}
            WHERE request_id = #{requestId} AND status = 'STOCK_RESERVED'
            """)
    int markMessageSent(@Param("requestId") String requestId,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE seckill_request
            SET status = 'ORDER_CREATED',
                order_no = #{orderNo},
                updated_at = #{now}
            WHERE request_id = #{requestId}
              AND user_id = #{userId}
              AND activity_id = #{activityId}
              AND status IN ('STOCK_RESERVED', 'MESSAGE_SENT', 'ORDER_CREATED')
            """)
    int markOrderCreated(@Param("requestId") String requestId,
                         @Param("userId") long userId,
                         @Param("activityId") long activityId,
                         @Param("orderNo") String orderNo,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE seckill_request
            SET status = 'CANCELLED',
                failure_code = #{reason},
                updated_at = #{now}
            WHERE order_no = #{orderNo}
              AND user_id = #{userId}
              AND activity_id = #{activityId}
              AND status = 'ORDER_CREATED'
            """)
    int markCancelled(@Param("orderNo") String orderNo,
                      @Param("userId") long userId,
                      @Param("activityId") long activityId,
                      @Param("reason") String reason,
                      @Param("now") LocalDateTime now);
}
