-- Async orchestration: park a saga on a correlation key while it awaits a Kafka reply.
ALTER TABLE saga_instance ADD COLUMN await_correlation_key VARCHAR(255);
ALTER TABLE saga_instance ADD COLUMN await_step VARCHAR(255);

CREATE INDEX ix_saga_await_correlation_key ON saga_instance (await_correlation_key);
