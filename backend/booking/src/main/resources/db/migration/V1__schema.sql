CREATE TABLE branch (
                        id            UUID PRIMARY KEY,
                        name          VARCHAR(100) NOT NULL,
                        address       VARCHAR(255) NOT NULL,
                        timezone      VARCHAR(50)  NOT NULL DEFAULT 'Africa/Johannesburg',
                        slot_minutes  INT          NOT NULL DEFAULT 30 CHECK (slot_minutes BETWEEN 10 AND 120)
);

CREATE TABLE branch_opening_hours (
                                      branch_id    UUID    NOT NULL REFERENCES branch(id),
                                      day_of_week  INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),   -- ISO: 1=Mon .. 7=Sun
                                      open_time    TIME    NOT NULL,
                                      close_time   TIME    NOT NULL,
                                      PRIMARY KEY (branch_id, day_of_week),
                                      CHECK (close_time > open_time)
);

CREATE TABLE service_type (
                              id    UUID PRIMARY KEY,
                              code  VARCHAR(30)  NOT NULL UNIQUE,
                              name  VARCHAR(100) NOT NULL
);

CREATE TABLE appointment (
                             id                UUID PRIMARY KEY,
                             reference         VARCHAR(12)  NOT NULL UNIQUE,
                             branch_id         UUID         NOT NULL REFERENCES branch(id),
                             service_type_id   UUID         NOT NULL REFERENCES service_type(id),
                             start_at          TIMESTAMPTZ  NOT NULL,
                             end_at            TIMESTAMPTZ  NOT NULL,
                             customer_name     VARCHAR(100) NOT NULL,
                             customer_email    VARCHAR(255) NOT NULL,
                             customer_phone    VARCHAR(20)  NOT NULL,
                             status            VARCHAR(20)  NOT NULL CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
                             idempotency_key   VARCHAR(64)  UNIQUE,
                             reschedule_count  INT          NOT NULL DEFAULT 0,
                             version           BIGINT       NOT NULL DEFAULT 0,
                             created_at        TIMESTAMPTZ  NOT NULL,
                             CHECK (end_at > start_at)
);

-- The double-booking guard: one CONFIRMED booking per branch per slot.
-- Cancelling or moving a booking frees the slot automatically.
CREATE UNIQUE INDEX uq_active_slot ON appointment (branch_id, start_at) WHERE status = 'CONFIRMED';

CREATE TABLE appointment_history (
                                     id             UUID PRIMARY KEY,
                                     appointment_id UUID        NOT NULL REFERENCES appointment(id),
                                     event_type     VARCHAR(20) NOT NULL CHECK (event_type IN ('CREATED','RESCHEDULED','CANCELLED')),
                                     old_start_at   TIMESTAMPTZ,
                                     new_start_at   TIMESTAMPTZ,
                                     occurred_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_history_appointment ON appointment_history (appointment_id);

CREATE TABLE notification (
                              id             UUID PRIMARY KEY,
                              appointment_id UUID         NOT NULL REFERENCES appointment(id),
                              type           VARCHAR(20)  NOT NULL,
                              channel        VARCHAR(10)  NOT NULL CHECK (channel IN ('EMAIL','SMS')),
                              recipient      VARCHAR(255) NOT NULL,   -- stored MASKED, never the raw address
                              body           TEXT         NOT NULL,
                              status         VARCHAR(10)  NOT NULL CHECK (status IN ('SENT','FAILED')),
                              created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_notification_appointment ON notification (appointment_id);