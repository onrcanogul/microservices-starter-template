CREATE TABLE inbox (
    idempotent_token    UUID            PRIMARY KEY,
    type                VARCHAR(512)    NOT NULL,
    payload             TEXT            NOT NULL,
    is_processed        BOOLEAN         NOT NULL DEFAULT FALSE,
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX ix_inbox_is_processed ON inbox (is_processed) WHERE is_processed = FALSE;
