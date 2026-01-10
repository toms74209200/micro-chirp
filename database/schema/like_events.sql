CREATE TABLE like_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);

CREATE INDEX idx_like_events_post_id ON like_events (post_id);
CREATE INDEX idx_like_events_user_id ON like_events (user_id);
CREATE INDEX idx_like_events_post_user ON like_events (post_id, user_id);
CREATE INDEX idx_like_events_occurred_at ON like_events (occurred_at);
CREATE INDEX idx_like_events_event_type ON like_events (event_type);
