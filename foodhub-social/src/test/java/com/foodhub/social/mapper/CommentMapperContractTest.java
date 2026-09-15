package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.social.entity.CommentEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentMapperContractTest {

    @Test
    void mapperOnlyReturnsVisibleCommentsInStableOrder() throws Exception {
        assertTrue(BaseMapper.class.isAssignableFrom(CommentMapper.class));
        Select page = CommentMapper.class
                .getMethod("selectVisiblePage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", page.value());

        assertTrue(sql.contains("post_id = #{postId}"));
        assertTrue(sql.contains("status = 'VISIBLE'"));
        assertTrue(sql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }

    @Test
    void commentDeleteAndCounterUpdateAreConditional() throws Exception {
        Update delete = CommentMapper.class
                .getMethod("softDeleteVisible", long.class, long.class, long.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        String deleteSql = String.join(" ", delete.value());
        assertTrue(deleteSql.contains("author_id = #{authorId}"));
        assertTrue(deleteSql.contains("status = 'VISIBLE'"));

        Update decrement = CommentMapper.class
                .getMethod("decrementCommentCount", long.class).getAnnotation(Update.class);
        assertTrue(String.join(" ", decrement.value()).contains("comment_count > 0"));
    }
}
