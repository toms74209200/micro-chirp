CREATE TABLE view_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);

CREATE INDEX idx_view_events_post_id ON view_events (post_id);
CREATE INDEX idx_view_events_user_id ON view_events (user_id);
CREATE INDEX idx_view_events_post_user ON view_events (post_id, user_id);
