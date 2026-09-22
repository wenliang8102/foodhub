package com.foodhub.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.order.entity.OrderOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderOutboxMapper extends BaseMapper<OrderOutboxEntity> {

    @Update("""
            UPDATE order_outbox
            SET status = 'SENT', last_error = NULL, updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markSent(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE order_outbox
            SET attempts = attempts + 1,
                next_retry_at = #{nextRetryAt},
                last_error = #{error},
                updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markFailed(@Param("id") long id,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("error") String error,
                   @Param("now") LocalDateTime now);
}
