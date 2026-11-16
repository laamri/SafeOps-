CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    plan VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    config_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
