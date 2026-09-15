package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.ModerationLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModerationLogMapper extends BaseMapper<ModerationLogEntity> {

    @Insert("""
            INSERT INTO post_moderation_log
                (post_id, operator_id, from_status, to_status, reason, created_at)
            VALUES
                (#{postId}, #{operatorId}, #{fromStatus}, #{toStatus}, #{reason}, #{createdAt})
            """)
    int insertLog(ModerationLogEntity log);
}
