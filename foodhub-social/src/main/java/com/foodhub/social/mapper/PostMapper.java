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
                   favorite_count AS favoriteCount,
                   comment_count AS commentCount,
                   hidden_at AS hiddenAt,
                   hidden_by AS hiddenBy,
                   hidden_reason AS hiddenReason
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
                   favorite_count AS favoriteCount,
                   comment_count AS commentCount,
                   hidden_at AS hiddenAt,
                   hidden_by AS hiddenBy,
                   hidden_reason AS hiddenReason
            FROM post
            WHERE id = #{postId} AND status = 'VISIBLE'
            """)
    PostEntity selectVisibleById(@Param("postId") long postId);

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
                   favorite_count AS favoriteCount,
                   comment_count AS commentCount,
                   hidden_at AS hiddenAt,
                   hidden_by AS hiddenBy,
                   hidden_reason AS hiddenReason
            FROM post
            WHERE id = #{postId}
            """)
    PostEntity selectAnyById(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET status = #{toStatus},
                updated_at = #{updatedAt},
                hidden_at = CASE WHEN #{toStatus} = 'HIDDEN' THEN #{updatedAt} ELSE hidden_at END,
                hidden_by = CASE WHEN #{toStatus} = 'HIDDEN' THEN #{operatorId} ELSE hidden_by END,
                hidden_reason = CASE WHEN #{toStatus} = 'HIDDEN' THEN #{reason} ELSE hidden_reason END
            WHERE id = #{postId}
              AND status = #{fromStatus}
            """)
    int updateVisibility(@Param("postId") long postId,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("operatorId") long operatorId,
                         @Param("reason") String reason,
                         @Param("updatedAt") LocalDateTime updatedAt);

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
            FROM post p
            JOIN user_follow f ON f.following_id = p.author_id
            WHERE f.follower_id = #{viewerUserId}
              AND p.status = 'VISIBLE'
            ORDER BY p.published_at DESC, p.id DESC
            LIMIT #{limit}
            """)
    List<PostEntity> selectFeedFirstPage(@Param("viewerUserId") long viewerUserId,
                                         @Param("limit") int limit);

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
            FROM post p
            JOIN user_follow f ON f.following_id = p.author_id
            WHERE f.follower_id = #{viewerUserId}
              AND p.status = 'VISIBLE'
              AND (
                    p.published_at < #{beforePublishedAt}
                    OR (p.published_at = #{beforePublishedAt} AND p.id < #{beforePostId})
                  )
            ORDER BY p.published_at DESC, p.id DESC
            LIMIT #{limit}
            """)
    List<PostEntity> selectFeedAfterCursor(@Param("viewerUserId") long viewerUserId,
                                           @Param("beforePublishedAt") LocalDateTime beforePublishedAt,
                                           @Param("beforePostId") long beforePostId,
                                           @Param("limit") int limit);

    @Update("""
            UPDATE post
            SET like_count = #{likeCount},
                favorite_count = #{favoriteCount},
                comment_count = #{commentCount},
                updated_at = #{updatedAt}
            WHERE id = #{postId}
            """)
    int updateCounters(@Param("postId") long postId,
                       @Param("likeCount") long likeCount,
                       @Param("favoriteCount") long favoriteCount,
                       @Param("commentCount") long commentCount,
                       @Param("updatedAt") LocalDateTime updatedAt);

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
