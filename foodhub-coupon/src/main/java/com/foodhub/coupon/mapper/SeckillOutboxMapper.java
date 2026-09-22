package com.foodhub.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.coupon.entity.SeckillOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SeckillOutboxMapper extends BaseMapper<SeckillOutboxEntity> {

    @Update("""
            UPDATE seckill_outbox
            SET status = 'SENT',
                updated_at = #{now},
                last_error = NULL
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markSent(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE seckill_outbox
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
