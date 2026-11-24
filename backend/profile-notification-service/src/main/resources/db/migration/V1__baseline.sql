-- Baseline of the schema Hibernate created before Flyway took over. Existing databases are
-- marked as baselined (see spring.flyway.baseline-on-migrate) so this script only runs on a
-- fresh database; it must therefore match the entities exactly.

CREATE TABLE profile_preferences (
    subject varchar(255) NOT NULL,
    appointment_reminder boolean NOT NULL,
    email boolean NOT NULL,
    language varchar(16) NOT NULL,
    sms boolean NOT NULL,
    CONSTRAINT profile_preferences_pkey PRIMARY KEY (subject)
);

CREATE TABLE user_notification (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    message varchar(1000) NOT NULL,
    is_read boolean NOT NULL,
    subject varchar(255) NOT NULL,
    type varchar(40) NOT NULL,
    CONSTRAINT user_notification_pkey PRIMARY KEY (id),
    CONSTRAINT user_notification_type_check CHECK (type IN ('TURN_APPROACHING', 'APPOINTMENT_REMINDER'))
);

CREATE INDEX idxaedqm5px7wv40btyv1akasifa ON user_notification (subject, created_at);
