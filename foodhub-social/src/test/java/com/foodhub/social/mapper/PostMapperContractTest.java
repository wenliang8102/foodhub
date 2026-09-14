package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMapperContractTest {

    @Test
    void mapperOnlyReadsVisiblePostsInStableNewestFirstOrder() throws Exception {
        assertTrue(BaseMapper.class.isAssignableFrom(PostMapper.class));

        Select pageSelect = PostMapper.class
                .getMethod("selectVisiblePage", long.class, int.class)
                .getAnnotation(Select.class);
        String pageSql = String.join(" ", pageSelect.value());

        assertTrue(pageSql.contains("WHERE status = 'VISIBLE'"));
        assertTrue(pageSql.contains("ORDER BY published_at DESC, id DESC"));
        assertTrue(pageSql.contains("LIMIT #{limit} OFFSET #{offset}"));

        Select detailSelect = PostMapper.class
                .getMethod("selectVisibleById", long.class)
                .getAnnotation(Select.class);
        String detailSql = String.join(" ", detailSelect.value());

        assertTrue(detailSql.contains("WHERE id = #{postId} AND status = 'VISIBLE'"));
    }

    @Test
    void softDeleteIsConditionalOnAuthorAndVisibleStatus() throws Exception {
        Update update = PostMapper.class
                .getMethod("softDelete", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", update.value());

        assertTrue(sql.contains("SET status = 'DELETED'"));
        assertTrue(sql.contains("author_id = #{authorId}"));
        assertTrue(sql.contains("status = 'VISIBLE'"));
    }
}
