CREATE TABLE stock (
    id          BIGSERIAL       PRIMARY KEY,
    sku         VARCHAR(255)    NOT NULL UNIQUE,
    available   INTEGER         NOT NULL,
    reserved    INTEGER         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  VARCHAR(255)    NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE stock_reservation (
    id          BIGSERIAL       PRIMARY KEY,
    order_id    BIGINT          NOT NULL UNIQUE,
    sku         VARCHAR(255)    NOT NULL,
    amount      INTEGER         NOT NULL,
    status      VARCHAR(32)     NOT NULL DEFAULT 'RESERVED',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  VARCHAR(255)    NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ
);

-- order_id is UNIQUE (one reservation per order) — that is the inventory-side idempotency key.

-- Seed stock so the choreography is observable and testable out of the box.
-- SKU-001 / SKU-002 have stock; SKU-OUT-OF-STOCK forces the reservation-failed path.
INSERT INTO stock (sku, available, reserved, created_by) VALUES
    ('SKU-001', 100, 0, 'SYSTEM'),
    ('SKU-002', 50, 0, 'SYSTEM'),
    ('SKU-OUT-OF-STOCK', 0, 0, 'SYSTEM');
