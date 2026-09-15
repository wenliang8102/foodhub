package com.foodhub.social.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionMapperContractTest {

    @Test
    void factsUseUniqueConstraintForIdempotentWrites() throws Exception {
        Insert like = InteractionMapper.class
                .getMethod("insertLikeIfAbsent", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Insert.class);
        Insert favorite = InteractionMapper.class
                .getMethod("insertFavoriteIfAbsent", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Insert.class);
        assertTrue(sql(like.value()).contains("INSERT INTO post_like"));
        assertTrue(sql(like.value()).contains("ON DUPLICATE KEY UPDATE id = id"));
        assertTrue(sql(favorite.value()).contains("INSERT INTO post_favorite"));
        assertTrue(sql(favorite.value()).contains("ON DUPLICATE KEY UPDATE id = id"));

        Delete deleteLike = InteractionMapper.class
                .getMethod("deleteLike", long.class, long.class).getAnnotation(Delete.class);
        Delete deleteFavorite = InteractionMapper.class
                .getMethod("deleteFavorite", long.class, long.class).getAnnotation(Delete.class);
        assertTrue(sql(deleteLike.value()).contains("user_id = #{userId}"));
        assertTrue(sql(deleteFavorite.value()).contains("post_id = #{postId}"));
    }

    @Test
    void counterUpdatesAreAtomicAndNeverDecrementBelowZero() throws Exception {
        Update incrementLike = InteractionMapper.class
                .getMethod("incrementLikeCount", long.class).getAnnotation(Update.class);
        Update decrementLike = InteractionMapper.class
                .getMethod("decrementLikeCount", long.class).getAnnotation(Update.class);
        Update incrementFavorite = InteractionMapper.class
                .getMethod("incrementFavoriteCount", long.class).getAnnotation(Update.class);
        Update decrementFavorite = InteractionMapper.class
                .getMethod("decrementFavoriteCount", long.class).getAnnotation(Update.class);

        assertTrue(sql(incrementLike.value()).contains("like_count = like_count + 1"));
        assertTrue(sql(decrementLike.value()).contains("like_count > 0"));
        assertTrue(sql(incrementFavorite.value()).contains("favorite_count = favorite_count + 1"));
        assertTrue(sql(decrementFavorite.value()).contains("favorite_count > 0"));
    }

    @Test
    void favoritePageOnlyReturnsVisiblePostsInStableFavoriteOrder() throws Exception {
        Select page = InteractionMapper.class
                .getMethod("selectVisibleFavoritePage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String sql = sql(page.value());
        assertTrue(sql.contains("JOIN post p ON p.id = f.post_id"));
        assertTrue(sql.contains("p.status = 'VISIBLE'"));
        assertTrue(sql.contains("ORDER BY f.created_at DESC, f.id DESC"));
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }

    private String sql(String[] fragments) {
        return String.join(" ", fragments);
    }
}
