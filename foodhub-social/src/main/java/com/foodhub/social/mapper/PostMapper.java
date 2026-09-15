package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.PostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    @Select("""
            SELECT id,
                   author_id AS authorId,
                   content,
                   image_urls AS imageUrls,
                   merchant_id AS merchantId,
                   status,
                   published_at AS publishedAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt,
                   like_count AS likeCount,
                   favorite_count AS favoriteCount
            FROM post
            WHERE status = 'VISIBLE'
            ORDER BY published_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostEntity> selectVisiblePage(@Param("offset") long offset,
                                       @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM post WHERE status = 'VISIBLE'")
    long countVisible();

    @Select("""
            SELECT id,
                   author_id AS authorId,
                   content,
                   image_urls AS imageUrls,
                   merchant_id AS merchantId,
                   status,
                   published_at AS publishedAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt,
                   like_count AS likeCount,
                   favorite_count AS favoriteCount
            FROM post
            WHERE id = #{postId} AND status = 'VISIBLE'
            """)
    PostEntity selectVisibleById(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET status = 'DELETED',
                updated_at = #{deletedAt},
                deleted_at = #{deletedAt}
            WHERE id = #{postId}
              AND author_id = #{authorId}
              AND status = 'VISIBLE'
            """)
    int softDelete(@Param("postId") long postId,
                   @Param("authorId") long authorId,
                   @Param("deletedAt") LocalDateTime deletedAt);
}
