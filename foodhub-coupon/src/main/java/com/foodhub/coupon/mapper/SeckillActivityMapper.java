package com.foodhub.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.coupon.entity.SeckillActivityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivityEntity> {

    @Update("""
            UPDATE seckill_activity
            SET remaining_stock = remaining_stock - 1,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{activityId}
              AND remaining_stock > 0
              AND version = #{version}
            """)
    int decrementStockOptimistically(@Param("activityId") long activityId,
                                     @Param("version") int version);

    @Update("""
            UPDATE seckill_activity
            SET remaining_stock = remaining_stock + #{quantity},
                version = version + 1,
                updated_at = #{now}
            WHERE id = #{activityId}
              AND remaining_stock + #{quantity} <= total_stock
            """)
    int restoreStock(@Param("activityId") long activityId,
                     @Param("quantity") int quantity,
                     @Param("now") java.time.LocalDateTime now);
}
