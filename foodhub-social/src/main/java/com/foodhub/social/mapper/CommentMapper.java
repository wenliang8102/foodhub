package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.CommentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {

    @Insert("""
            INSERT INTO post_comment
                (post_id, author_id, content, status, created_at, updated_at)
            VALUES
                (#{postId}, #{authorId}, #{content}, 'VISIBLE', #{createdAt}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVisible(CommentEntity comment);

    @Select("""
            SELECT COUNT(*)
            FROM post_comment
            WHERE post_id = #{postId}
              AND status = 'VISIBLE'
            """)
    long countVisibleByPostId(@Param("postId") long postId);

    @Select("""
            SELECT id,
                   post_id AS postId,
                   author_id AS authorId,
                   content,
                   status,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM post_comment
            WHERE post_id = #{postId}
              AND status = 'VISIBLE'
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<CommentEntity> selectVisiblePage(@Param("postId") long postId,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);

    @Select("""
            SELECT id,
                   post_id AS postId,
                   author_id AS authorId,
                   content,
                   status,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM post_comment
            WHERE id = #{commentId}
              AND post_id = #{postId}
            """)
    CommentEntity selectByPostAndId(@Param("postId") long postId,
                                    @Param("commentId") long commentId);

    @Update("""
            UPDATE post_comment
            SET status = 'DELETED',
                updated_at = #{deletedAt},
                deleted_at = #{deletedAt}
            WHERE id = #{commentId}
              AND post_id = #{postId}
              AND author_id = #{authorId}
              AND status = 'VISIBLE'
            """)
    int softDeleteVisible(@Param("postId") long postId,
                          @Param("commentId") long commentId,
                          @Param("authorId") long authorId,
                          @Param("deletedAt") LocalDateTime deletedAt);

    @Update("""
            UPDATE post
            SET comment_count = comment_count + 1
            WHERE id = #{postId}
              AND status = 'VISIBLE'
            """)
    int incrementCommentCount(@Param("postId") long postId);

    @Update("""
            UPDATE post
            SET comment_count = comment_count - 1
            WHERE id = #{postId}
              AND status = 'VISIBLE'
              AND comment_count > 0
            """)
    int decrementCommentCount(@Param("postId") long postId);
}
