-- Baseline of the schema Hibernate created before Flyway took over, including the partial
-- unique index that was previously re-applied by schema.sql. Existing databases are marked as
-- baselined (see spring.flyway.baseline-on-migrate), so this runs only on a fresh database.

CREATE TABLE appointment (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    establishment_id varchar(128) NOT NULL,
    owner_subject varchar(255) NOT NULL,
    queue_id bigint NOT NULL,
    reason varchar(500) NOT NULL,
    starts_at timestamp(6) with time zone NOT NULL,
    status varchar(32) NOT NULL,
    organization_id varchar(64),
    CONSTRAINT appointment_pkey PRIMARY KEY (id),
    CONSTRAINT appointment_status_check CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'))
);

CREATE INDEX idx2dntjn256vlal31bjmc78ly3 ON appointment (establishment_id, starts_at);
CREATE INDEX idxd0hm0gsve2jj2kg57ynj5n0mi ON appointment (organization_id, starts_at);
CREATE INDEX idxjskyr1cq0m6hlxjiw0kieu6i3 ON appointment (owner_subject, starts_at, status);
CREATE UNIQUE INDEX uq_appointment_booked_owner_start ON appointment (owner_subject, starts_at) WHERE status = 'BOOKED';
