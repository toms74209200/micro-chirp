DROP MATERIALIZED VIEW IF EXISTS global_timeline_mv;

CREATE MATERIALIZED VIEW global_timeline_mv AS
WITH created AS (
    SELECT
        pe.post_id,
        (pe.event_data ->> 'userId')::uuid AS user_id,
        pe.event_data ->> 'content' AS content,
        pe.occurred_at AS created_at
    FROM post_events pe
    WHERE pe.event_type = 'post_created'
),

deleted AS (
    SELECT post_id FROM post_events WHERE event_type = 'post_deleted'
)

SELECT c.post_id, c.user_id, c.content, c.created_at
FROM created c
WHERE c.post_id NOT IN (SELECT post_id FROM deleted);

CREATE UNIQUE INDEX IF NOT EXISTS idx_global_timeline_mv_post_id
ON global_timeline_mv (post_id);

CREATE INDEX IF NOT EXISTS idx_global_timeline_mv_created_at
ON global_timeline_mv (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_global_timeline_mv_user_id
ON global_timeline_mv (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS mv_refresh_log (
    view_name varchar(100) PRIMARY KEY,
    last_refreshed_at timestamptz NOT NULL DEFAULT current_timestamp
);

INSERT INTO mv_refresh_log (view_name)
VALUES ('global_timeline_mv') ON CONFLICT DO NOTHING;
