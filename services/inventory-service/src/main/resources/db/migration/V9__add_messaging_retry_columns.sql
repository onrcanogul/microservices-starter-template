-- Processing-layer poison-message handling: retry + dead-letter columns for inbox and outbox.
-- (Distinct from the Kafka transport DLT, which is unchanged.)

ALTER TABLE inbox  ADD COLUMN attempts        INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE inbox  ADD COLUMN last_error      TEXT;
ALTER TABLE inbox  ADD COLUMN dead            BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE inbox  ADD COLUMN next_attempt_at TIMESTAMPTZ;

ALTER TABLE outbox ADD COLUMN attempts        INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN last_error      TEXT;
ALTER TABLE outbox ADD COLUMN dead            BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE outbox ADD COLUMN next_attempt_at TIMESTAMPTZ;

-- Partial indexes for the eligible-batch scan: filter not-done & not-dead, ordered by poll order
-- (received_at / created_at, matching findEligible / findBatchToPublish).
CREATE INDEX ix_inbox_eligible  ON inbox  (received_at) WHERE is_processed = FALSE AND dead = FALSE;
CREATE INDEX ix_outbox_eligible ON outbox (created_at)  WHERE is_published = FALSE AND dead = FALSE;
