-- Revision info table for Hibernate Envers audit trail (inventory owns its own DB).
-- Stores metadata for each revision: who, when, correlationId. Populated by CustomRevisionListener.
CREATE TABLE revinfo (
    rev             BIGSERIAL       PRIMARY KEY,
    revtstmp        BIGINT          NOT NULL,
    user_id         VARCHAR(255),
    user_email      VARCHAR(255),
    correlation_id  VARCHAR(255)
);

-- Audit history for stock (Hibernate Envers): mirrors stock + rev FK + revtype.
CREATE TABLE stock_aud (
    id          BIGINT          NOT NULL,
    rev         BIGINT          NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    sku         VARCHAR(255),
    available   INTEGER,
    reserved    INTEGER,
    created_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);

-- Audit history for stock_reservation (Hibernate Envers).
CREATE TABLE stock_reservation_aud (
    id          BIGINT          NOT NULL,
    rev         BIGINT          NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    order_id    BIGINT,
    sku         VARCHAR(255),
    amount      INTEGER,
    status      VARCHAR(32),
    created_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);

CREATE INDEX idx_stock_aud_rev ON stock_aud(rev);
CREATE INDEX idx_stock_reservation_aud_rev ON stock_reservation_aud(rev);
CREATE INDEX idx_revinfo_user_id ON revinfo(user_id);
CREATE INDEX idx_revinfo_revtstmp ON revinfo(revtstmp);
