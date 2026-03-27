DROP MATERIALIZED VIEW IF EXISTS posts_mv;

CREATE MATERIALIZED VIEW posts_mv AS
SELECT
    post_id,
    MAX(
        CASE
            WHEN event_type = 'post_created'
                THEN JSONB_EXTRACT_PATH_TEXT(event_data, 'userId')
        END
    )::uuid AS user_id,
    MAX(
        CASE
            WHEN
                event_type = 'post_created'
                THEN JSONB_EXTRACT_PATH_TEXT(event_data, 'content')
        END
    ) AS content,
    MIN(
        CASE WHEN event_type = 'post_created' THEN occurred_at END
    ) AS created_at
FROM post_events
GROUP BY post_id
HAVING NOT BOOL_OR(event_type = 'post_deleted');

CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_mv_post_id
ON posts_mv (post_id);

CREATE INDEX IF NOT EXISTS idx_posts_mv_cursor
ON posts_mv (created_at DESC, post_id DESC);

CREATE INDEX IF NOT EXISTS idx_posts_mv_user_id
ON posts_mv (user_id, created_at DESC, post_id DESC);

CREATE TABLE IF NOT EXISTS mv_refresh_log (
    view_name varchar(100) PRIMARY KEY,
    last_refreshed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO mv_refresh_log (view_name, last_refreshed_at)
VALUES ('posts_mv', CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING;
