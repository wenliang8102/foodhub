package com.foodhub.social.mapper;

import com.foodhub.social.entity.PostEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InteractionMapper {

    @Insert("""
            INSERT INTO post_like (user_id, post_id, created_at)
            VALUES (#{userId}, #{postId}, #{createdAt})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertLikeIfAbsent(@Param("userId") long userId,
                           @Param("postId") long postId,
                           @Param("createdAt") LocalDateTime createdAt);

    @Delete("""
            DELETE FROM post_like
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            """)
    int deleteLike(@Param("userId") long userId,
                   @Param("postId") long postId);

    @Insert("""
            INSERT INTO post_favorite (user_id, post_id, created_at)
            VALUES (#{userId}, #{postId}, #{createdAt})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertFavoriteIfAbsent(@Param("userId") long userId,
                               @Param("postId") long postId,
                               @Param("createdAt") LocalDateTime createdAt);

    @Delete("""
            DELETE FROM post_favorite
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            """)
    int deleteFavorite(@Param("userId") long userId,
                       @Param("postId") long postId);

    @Update("""
            UPDATE post
            SET like_count = like_count + 1
            WHERE id = #{postId}
              AND status = 'VISIBLE'
            """)
    int incrementLikeCount(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET like_count = like_count - 1
            WHERE id = #{postId}
              AND status = 'VISIBLE'
              AND like_count > 0
            """)
    int decrementLikeCount(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET favorite_count = favorite_count + 1
            WHERE id = #{postId}
              AND status = 'VISIBLE'
            """)
    int incrementFavoriteCount(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET favorite_count = favorite_count - 1
            WHERE id = #{postId}
              AND status = 'VISIBLE'
              AND favorite_count > 0
            """)
    int decrementFavoriteCount(@Param("postId") long postId);

    @Select("""
            SELECT COUNT(*) > 0
            FROM post_like
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            """)
    boolean existsLike(@Param("userId") long userId,
                       @Param("postId") long postId);

    @Select("""
            SELECT COUNT(*) > 0
            FROM post_favorite
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            """)
    boolean existsFavorite(@Param("userId") long userId,
                           @Param("postId") long postId);

    @Select("""
            SELECT COUNT(*)
            FROM post_favorite f
            JOIN post p ON p.id = f.post_id
            WHERE f.user_id = #{userId}
              AND p.status = 'VISIBLE'
            """)
    long countVisibleFavorites(@Param("userId") long userId);

    @Select("""
            SELECT p.id,
                   p.author_id AS authorId,
                   p.content,
                   p.image_urls AS imageUrls,
                   p.merchant_id AS merchantId,
                   p.status,
                   p.published_at AS publishedAt,
                   p.updated_at AS updatedAt,
                   p.deleted_at AS deletedAt,
                   p.like_count AS likeCount,
                   p.favorite_count AS favoriteCount,
                   p.comment_count AS commentCount,
                   p.hidden_at AS hiddenAt,
                   p.hidden_by AS hiddenBy,
                   p.hidden_reason AS hiddenReason
            FROM post_favorite f
            JOIN post p ON p.id = f.post_id
            WHERE f.user_id = #{userId}
              AND p.status = 'VISIBLE'
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostEntity> selectVisibleFavoritePage(@Param("userId") long userId,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM post_like WHERE post_id = #{postId}")
    long countLikesByPostId(@Param("postId") long postId);

    @Select("SELECT COUNT(*) FROM post_favorite WHERE post_id = #{postId}")
    long countFavoritesByPostId(@Param("postId") long postId);
}
