CREATE TABLE orders (
    id          BIGSERIAL       PRIMARY KEY,
    sku         VARCHAR(255)    NOT NULL,
    amount      INTEGER         NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  VARCHAR(255)    NOT NULL,
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ
);
