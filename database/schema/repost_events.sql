CREATE TABLE repost_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);

CREATE INDEX idx_repost_events_post_id ON repost_events (post_id);
CREATE INDEX idx_repost_events_user_id ON repost_events (user_id);
CREATE INDEX idx_repost_events_post_user ON repost_events (post_id, user_id);
