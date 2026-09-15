package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.FollowEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<FollowEntity> {

    @Insert("""
            INSERT INTO user_follow (follower_id, following_id, created_at)
            VALUES (#{followerId}, #{followingId}, #{createdAt})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("followerId") long followerId,
                       @Param("followingId") long followingId,
                       @Param("createdAt") LocalDateTime createdAt);

    @Delete("""
            DELETE FROM user_follow
            WHERE follower_id = #{followerId}
              AND following_id = #{followingId}
            """)
    int deleteRelationship(@Param("followerId") long followerId,
                           @Param("followingId") long followingId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId}")
    long countFollowing(@Param("followerId") long followerId);

    @Select("""
            SELECT id,
                   follower_id AS followerId,
                   following_id AS followingId,
                   created_at AS createdAt
            FROM user_follow
            WHERE follower_id = #{followerId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FollowEntity> selectFollowingPage(@Param("followerId") long followerId,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM user_follow WHERE following_id = #{followingId}")
    long countFollowers(@Param("followingId") long followingId);

    @Select("""
            SELECT id,
                   follower_id AS followerId,
                   following_id AS followingId,
                   created_at AS createdAt
            FROM user_follow
            WHERE following_id = #{followingId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FollowEntity> selectFollowersPage(@Param("followingId") long followingId,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);
}
