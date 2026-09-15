CREATE INDEX idx_post_author_feed
    ON post (author_id, status, published_at, id);
