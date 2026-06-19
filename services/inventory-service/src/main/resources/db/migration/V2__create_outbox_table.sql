CREATE TABLE outbox (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregatetype   VARCHAR(255),
    aggregateid     VARCHAR(255),
    type            VARCHAR(512)    NOT NULL,
    payload         TEXT            NOT NULL,
    destination     VARCHAR(255)    NOT NULL,
    is_published    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX ix_outbox_is_published ON outbox (is_published) WHERE is_published = FALSE;
