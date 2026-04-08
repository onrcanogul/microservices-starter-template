CREATE TABLE saga_instance (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type       VARCHAR(255)    NOT NULL,
    correlation_id  UUID            NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    payload         TEXT,
    current_step    INT             NOT NULL DEFAULT 0,
    retry_count     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deadline_at     TIMESTAMPTZ
);

CREATE INDEX ix_saga_status ON saga_instance (status);
CREATE INDEX ix_saga_correlation ON saga_instance (correlation_id);

CREATE TABLE saga_step_execution (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_instance_id    UUID            NOT NULL REFERENCES saga_instance(id) ON DELETE CASCADE,
    step_name           VARCHAR(255)    NOT NULL,
    step_order          INT             NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    output              TEXT,
    failure_reason      VARCHAR(1024),
    executed_at         TIMESTAMPTZ,
    compensated_at      TIMESTAMPTZ
);

CREATE INDEX ix_step_saga_id ON saga_step_execution (saga_instance_id);
