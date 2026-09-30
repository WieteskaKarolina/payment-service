CREATE TABLE outbox_events (
   id UUID PRIMARY KEY,
   event_type VARCHAR(100) NOT NULL,
   aggregate_id UUID NOT NULL,
   payload TEXT NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE NOT NULL,
   published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (published_at, created_at);