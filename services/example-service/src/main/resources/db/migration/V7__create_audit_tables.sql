-- Revision info table for Hibernate Envers audit trail.
-- Stores metadata for each revision: who, when, correlationId.
CREATE TABLE revinfo (
    rev             BIGSERIAL       PRIMARY KEY,
    revtstmp        BIGINT          NOT NULL,
    user_id         VARCHAR(255),
    user_email      VARCHAR(255),
    correlation_id  VARCHAR(255)
);

-- Audit history table for orders (Hibernate Envers).
-- Mirrors orders table structure + rev foreign key + revtype column.
CREATE TABLE orders_aud (
    id          BIGINT          NOT NULL,
    rev         BIGINT          NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    sku         VARCHAR(255),
    amount      INTEGER,
    created_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);

CREATE INDEX idx_orders_aud_rev ON orders_aud(rev);
CREATE INDEX idx_revinfo_user_id ON revinfo(user_id);
CREATE INDEX idx_revinfo_revtstmp ON revinfo(revtstmp);
