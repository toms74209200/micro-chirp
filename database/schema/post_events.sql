CREATE TABLE post_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    reply_to_post_id UUID NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    CONSTRAINT unique_post_event_type UNIQUE (post_id, event_type)
);

CREATE INDEX idx_post_events_post_id ON post_events (post_id);
CREATE INDEX idx_post_events_occurred_at ON post_events (occurred_at);
CREATE INDEX idx_post_events_event_type ON post_events (event_type);
CREATE INDEX idx_post_events_reply_to_post_id ON post_events (reply_to_post_id);
