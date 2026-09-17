-- sessions: sessions, attendance, presence checks and whiteboards
CREATE SCHEMA IF NOT EXISTS sessions;

CREATE TABLE sessions.sessions (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32)   NOT NULL,
  booking_id       uuid          NOT NULL UNIQUE,
  student_id       uuid          NOT NULL,
  tutor_id         uuid          NOT NULL,
  scheduled_start  timestamptz   NOT NULL,
  scheduled_end    timestamptz   NOT NULL,
  room_name        varchar(120)  NOT NULL UNIQUE,
  started_at       timestamptz,
  ended_at         timestamptz,
  status           varchar(24)   NOT NULL,
  created_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_sessions_status CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','UNVERIFIED','ABANDONED','CANCELLED'))
);
CREATE INDEX ix_sessions_scheduled_start ON sessions.sessions (tenant_id, scheduled_start);

CREATE TABLE sessions.participations (
  id                 uuid         PRIMARY KEY,
  tenant_id          varchar(32)  NOT NULL,
  session_id         uuid         NOT NULL REFERENCES sessions.sessions(id),
  user_id            uuid         NOT NULL,
  role               varchar(8)   NOT NULL,
  joined_at          timestamptz,
  left_at            timestamptz,
  connected_seconds  integer      NOT NULL DEFAULT 0,
  CONSTRAINT uq_participations UNIQUE (session_id, user_id),
  CONSTRAINT ck_participations_role CHECK (role IN ('STUDENT','TUTOR'))
);

CREATE TABLE sessions.presence_checks (
  id            uuid         PRIMARY KEY,
  tenant_id     varchar(32)  NOT NULL,
  session_id    uuid         NOT NULL REFERENCES sessions.sessions(id),
  user_id       uuid         NOT NULL,
  code_hash     varchar(64)  NOT NULL,
  issued_at     timestamptz  NOT NULL DEFAULT now(),
  expires_at    timestamptz  NOT NULL,
  confirmed_at  timestamptz,
  attempts      smallint     NOT NULL DEFAULT 0,
  CONSTRAINT uq_presence_checks UNIQUE (session_id, user_id),
  CONSTRAINT ck_presence_checks_attempts CHECK (attempts <= 5)
);

CREATE TABLE sessions.whiteboards (
  id            uuid         PRIMARY KEY,
  tenant_id     varchar(32)  NOT NULL,
  session_id    uuid         NOT NULL UNIQUE REFERENCES sessions.sessions(id),
  content       jsonb        NOT NULL DEFAULT '{}',
  has_activity  boolean      NOT NULL DEFAULT false,
  updated_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE sessions.support_materials (
  id            uuid         PRIMARY KEY,
  tenant_id     varchar(32)  NOT NULL,
  session_id    uuid         NOT NULL REFERENCES sessions.sessions(id),
  summary       text         NOT NULL,
  suggestions   jsonb,
  generated_at  timestamptz  NOT NULL DEFAULT now()
);
