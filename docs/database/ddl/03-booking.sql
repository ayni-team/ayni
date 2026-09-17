-- booking: availability, hour blocks and reservations
CREATE SCHEMA IF NOT EXISTS booking;

CREATE TABLE booking.availability_patterns (
  id              uuid         PRIMARY KEY,
  tenant_id       varchar(32)  NOT NULL,
  tutor_id        uuid         NOT NULL,
  day_of_week     smallint     NOT NULL,
  starts_at_time  time         NOT NULL,
  ends_at_time    time         NOT NULL,
  valid_from      date         NOT NULL,
  valid_until     date,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_availability_patterns_day CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT ck_availability_patterns_range CHECK (ends_at_time > starts_at_time)
);
CREATE INDEX ix_availability_patterns_tutor ON booking.availability_patterns (tenant_id, tutor_id);

CREATE TABLE booking.availability_exceptions (
  id              uuid         PRIMARY KEY,
  tenant_id       varchar(32)  NOT NULL,
  tutor_id        uuid         NOT NULL,
  exception_date  date         NOT NULL,
  starts_at_time  time,
  ends_at_time    time,
  kind            varchar(8)   NOT NULL,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_availability_exceptions_kind CHECK (kind IN ('ADD','REMOVE'))
);
CREATE INDEX ix_availability_exceptions_tutor_date ON booking.availability_exceptions (tenant_id, tutor_id, exception_date);

CREATE TABLE booking.availability_pauses (
  id          uuid         PRIMARY KEY,
  tenant_id   varchar(32)  NOT NULL,
  tutor_id    uuid         NOT NULL,
  starts_on   date         NOT NULL,
  ends_on     date         NOT NULL,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_availability_pauses_range CHECK (ends_on >= starts_on)
);

CREATE TABLE booking.bookings (
  id                   uuid         PRIMARY KEY,
  tenant_id            varchar(32)  NOT NULL,
  student_id           uuid         NOT NULL,
  tutor_id             uuid         NOT NULL,
  catalog_item_id      uuid         NOT NULL,
  starts_at            timestamptz  NOT NULL,
  ends_at              timestamptz  NOT NULL,
  hours                smallint     NOT NULL,
  credits_charged      integer      NOT NULL,
  need_description     text         NOT NULL,
  status               varchar(16)  NOT NULL,
  cancelled_by         varchar(16),
  cancelled_at         timestamptz,
  cancelled_late       boolean,
  cancellation_reason  varchar(500),
  created_at           timestamptz  NOT NULL DEFAULT now(),
  updated_at           timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
  CONSTRAINT ck_bookings_price CHECK (hours >= 1 AND credits_charged = hours),
  CONSTRAINT ck_bookings_range CHECK (ends_at > starts_at),
  CONSTRAINT ck_bookings_cancelled_by CHECK (cancelled_by IN ('STUDENT','TUTOR','SYSTEM')),
  CONSTRAINT ck_bookings_cancellation CHECK (status <> 'CANCELLED' OR (cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL))
);
CREATE INDEX ix_bookings_student ON booking.bookings (tenant_id, student_id, starts_at DESC);
CREATE INDEX ix_bookings_tutor ON booking.bookings (tenant_id, tutor_id, starts_at DESC);

CREATE TABLE booking.hour_blocks (
  id          uuid         PRIMARY KEY,
  tenant_id   varchar(32)  NOT NULL,
  tutor_id    uuid         NOT NULL,
  starts_at   timestamptz  NOT NULL,
  ends_at     timestamptz  NOT NULL,
  status      varchar(16)  NOT NULL,
  held_by     uuid,
  held_until  timestamptz,
  booking_id  uuid         REFERENCES booking.bookings(id),
  pattern_id  uuid         REFERENCES booking.availability_patterns(id),
  version     bigint       NOT NULL DEFAULT 0,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_hour_blocks_tutor_start UNIQUE (tenant_id, tutor_id, starts_at),
  CONSTRAINT ck_hour_blocks_status CHECK (status IN ('AVAILABLE','HELD','BOOKED','RELEASED')),
  CONSTRAINT ck_hour_blocks_booked CHECK ((status = 'BOOKED') = (booking_id IS NOT NULL)),
  CONSTRAINT ck_hour_blocks_held CHECK ((status = 'HELD') = (held_until IS NOT NULL AND held_by IS NOT NULL))
);
CREATE INDEX ix_hour_blocks_available ON booking.hour_blocks (tenant_id, starts_at) WHERE status = 'AVAILABLE';
CREATE INDEX ix_hour_blocks_held ON booking.hour_blocks (held_until) WHERE status = 'HELD';

CREATE TABLE booking.tutor_reliability (
  id           uuid         PRIMARY KEY,
  tenant_id    varchar(32)  NOT NULL,
  tutor_id     uuid         NOT NULL,
  booking_id   uuid         NOT NULL REFERENCES booking.bookings(id),
  kind         varchar(24)  NOT NULL,
  occurred_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_tutor_reliability UNIQUE (booking_id, kind),
  CONSTRAINT ck_tutor_reliability_kind CHECK (kind IN ('LATE_CANCELLATION','NO_SHOW'))
);
CREATE INDEX ix_tutor_reliability_tutor ON booking.tutor_reliability (tenant_id, tutor_id);
