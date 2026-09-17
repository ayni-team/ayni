-- identity: universities, students, coordinators and academic profiles
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.tenants (
  id                     uuid          PRIMARY KEY,
  code                   varchar(32)   NOT NULL UNIQUE,
  name                   varchar(160)  NOT NULL,
  logo_url               varchar(512),
  primary_color          varchar(16),
  secondary_color        varchar(16),
  email_domains          text[]        NOT NULL DEFAULT '{}',
  minimum_teaching_grade numeric(4,2)  NOT NULL,
  timezone               varchar(64)   NOT NULL DEFAULT 'America/Lima',
  status                 varchar(16)   NOT NULL,
  created_at             timestamptz   NOT NULL DEFAULT now(),
  updated_at             timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE','SUSPENDED'))
);
CREATE INDEX ix_tenants_email_domains ON identity.tenants USING gin (email_domains);

CREATE TABLE identity.platform_admins (
  id          uuid          PRIMARY KEY,
  email       varchar(160)  NOT NULL UNIQUE,
  full_name   varchar(160)  NOT NULL,
  status      varchar(16)   NOT NULL,
  created_at  timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_platform_admins_status CHECK (status IN ('ACTIVE','DISABLED'))
);

CREATE TABLE identity.users (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32)   NOT NULL,
  role             varchar(16)   NOT NULL,
  email            varchar(160)  NOT NULL,
  student_code     varchar(32),
  full_name        varchar(160)  NOT NULL,
  career           varchar(160),
  current_term     varchar(16),
  photo_url        varchar(512),
  bio              varchar(500),
  status           varchar(16)   NOT NULL,
  onboarding_step  varchar(32),
  activated_at     timestamptz,
  created_at       timestamptz   NOT NULL DEFAULT now(),
  updated_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
  CONSTRAINT uq_users_tenant_student_code UNIQUE (tenant_id, student_code),
  CONSTRAINT ck_users_role CHECK (role IN ('STUDENT','COORDINATOR')),
  CONSTRAINT ck_users_status CHECK (status IN ('PENDING','ACTIVE','RESTRICTED')),
  CONSTRAINT ck_users_student_code CHECK (role = 'COORDINATOR' OR student_code IS NOT NULL)
);

CREATE TABLE identity.access_links (
  id            uuid          PRIMARY KEY,
  tenant_id     varchar(32),
  email         varchar(160)  NOT NULL,
  purpose       varchar(24)   NOT NULL,
  token_hash    varchar(64)   NOT NULL UNIQUE,
  expires_at    timestamptz   NOT NULL,
  consumed_at   timestamptz,
  requested_ip  varchar(45),
  created_at    timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_access_links_purpose CHECK (purpose IN ('ACTIVATION','LOGIN','COORDINATOR_INVITE'))
);
CREATE INDEX ix_access_links_pending ON identity.access_links (email, purpose) WHERE consumed_at IS NULL;

CREATE TABLE identity.user_sessions (
  id          uuid          PRIMARY KEY,
  tenant_id   varchar(32),
  user_id     uuid          REFERENCES identity.users(id),
  admin_id    uuid          REFERENCES identity.platform_admins(id),
  token_hash  varchar(64)   NOT NULL UNIQUE,
  expires_at  timestamptz   NOT NULL,
  revoked_at  timestamptz,
  created_at  timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_user_sessions_owner CHECK (num_nonnulls(user_id, admin_id) = 1)
);

CREATE TABLE identity.academic_records (
  id           uuid          PRIMARY KEY,
  tenant_id    varchar(32)   NOT NULL,
  user_id      uuid          NOT NULL REFERENCES identity.users(id),
  course_code  varchar(32)   NOT NULL,
  course_name  varchar(160)  NOT NULL,
  grade        numeric(4,2)  NOT NULL,
  term         varchar(16)   NOT NULL,
  synced_at    timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_academic_records UNIQUE (tenant_id, user_id, course_code, term)
);
CREATE INDEX ix_academic_records_user ON identity.academic_records (tenant_id, user_id);

CREATE TABLE identity.credit_policies (
  id                uuid          PRIMARY KEY,
  tenant_id         varchar(32)   NOT NULL,
  kind              varchar(16)   NOT NULL,
  credits_amount    integer       NOT NULL,
  validity_days     integer       NOT NULL,
  target_rule       jsonb,
  valid_from        date          NOT NULL,
  superseded_at     timestamptz,
  created_by_admin  uuid          REFERENCES identity.platform_admins(id),
  created_by_user   uuid          REFERENCES identity.users(id),
  created_at        timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_credit_policies_kind CHECK (kind IN ('BASELINE','TARGETED')),
  CONSTRAINT ck_credit_policies_amounts CHECK (credits_amount > 0 AND validity_days > 0),
  CONSTRAINT ck_credit_policies_author CHECK (num_nonnulls(created_by_admin, created_by_user) = 1)
);
CREATE INDEX ix_credit_policies_current ON identity.credit_policies (tenant_id, kind) WHERE superseded_at IS NULL;
-- skills: catalogue of courses and tools, offered skills and accreditations
CREATE SCHEMA IF NOT EXISTS skills;

CREATE TABLE skills.categories (
  id          uuid         PRIMARY KEY,
  name        varchar(80)  NOT NULL UNIQUE,
  sort_order  smallint     NOT NULL DEFAULT 0
);

CREATE TABLE skills.catalog_items (
  id           uuid          PRIMARY KEY,
  scope        varchar(16)   NOT NULL,
  tenant_id    varchar(32),
  category_id  uuid          NOT NULL REFERENCES skills.categories(id),
  name         varchar(160)  NOT NULL,
  description  varchar(500),
  course_code  varchar(32),
  status       varchar(16)   NOT NULL,
  created_at   timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_catalog_items_scope CHECK (scope IN ('GLOBAL','UNIVERSITY')),
  CONSTRAINT ck_catalog_items_scope_tenant CHECK ((scope = 'GLOBAL') = (tenant_id IS NULL)),
  CONSTRAINT ck_catalog_items_course_code CHECK (scope = 'GLOBAL' OR course_code IS NOT NULL),
  CONSTRAINT ck_catalog_items_status CHECK (status IN ('ACTIVE','RETIRED')),
  CONSTRAINT uq_catalog_items_course UNIQUE (tenant_id, course_code)
);
CREATE INDEX ix_catalog_items_tenant_status ON skills.catalog_items (tenant_id, status);

CREATE TABLE skills.offered_skills (
  id                  uuid          PRIMARY KEY,
  tenant_id           varchar(32)   NOT NULL,
  tutor_id            uuid          NOT NULL,
  catalog_item_id     uuid          NOT NULL REFERENCES skills.catalog_items(id),
  status              varchar(16)   NOT NULL,
  accreditation_path  varchar(24),
  accredited_grade    numeric(4,2),
  enabled_at          timestamptz,
  created_at          timestamptz   NOT NULL DEFAULT now(),
  updated_at          timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_offered_skills UNIQUE (tenant_id, tutor_id, catalog_item_id),
  CONSTRAINT ck_offered_skills_status CHECK (status IN ('PENDING','ENABLED','REJECTED','WITHDRAWN')),
  CONSTRAINT ck_offered_skills_path CHECK (accreditation_path IN ('ACADEMIC_RECORD','REVIEWED_EVIDENCE')),
  CONSTRAINT ck_offered_skills_enabled_path CHECK (status <> 'ENABLED' OR accreditation_path IS NOT NULL)
);
CREATE INDEX ix_offered_skills_enabled ON skills.offered_skills (tenant_id, catalog_item_id) WHERE status = 'ENABLED';

CREATE TABLE skills.validation_requests (
  id                uuid           PRIMARY KEY,
  tenant_id         varchar(32)    NOT NULL,
  offered_skill_id  uuid           NOT NULL REFERENCES skills.offered_skills(id),
  student_note      varchar(1000),
  status            varchar(16)    NOT NULL,
  reviewed_by       uuid,
  reviewed_at       timestamptz,
  decision_reason   varchar(500),
  created_at        timestamptz    NOT NULL DEFAULT now(),
  CONSTRAINT ck_validation_requests_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
  CONSTRAINT ck_validation_requests_review CHECK (status = 'SUBMITTED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX ix_validation_requests_pending ON skills.validation_requests (tenant_id, status) WHERE status = 'SUBMITTED';

CREATE TABLE skills.evidence_files (
  id                     uuid          PRIMARY KEY,
  tenant_id              varchar(32)   NOT NULL,
  validation_request_id  uuid          NOT NULL REFERENCES skills.validation_requests(id),
  file_name              varchar(255)  NOT NULL,
  storage_key            varchar(512)  NOT NULL,
  content_type           varchar(100)  NOT NULL,
  size_bytes             bigint        NOT NULL,
  uploaded_at            timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE skills.learning_interests (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32)   NOT NULL,
  student_id       uuid          NOT NULL,
  catalog_item_id  uuid          NOT NULL REFERENCES skills.catalog_items(id),
  created_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_learning_interests UNIQUE (tenant_id, student_id, catalog_item_id)
);
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
-- wallet: credit accounts, credit groups and ledger entries
CREATE SCHEMA IF NOT EXISTS wallet;

CREATE TABLE wallet.credit_accounts (
  id          uuid         PRIMARY KEY,
  tenant_id   varchar(32)  NOT NULL,
  user_id     uuid         NOT NULL,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_credit_accounts_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE wallet.credit_lots (
  id                uuid         PRIMARY KEY,
  tenant_id         varchar(32)  NOT NULL,
  account_id        uuid         NOT NULL REFERENCES wallet.credit_accounts(id),
  credit_type       varchar(16)  NOT NULL,
  original_amount   integer      NOT NULL,
  remaining_amount  integer      NOT NULL,
  expires_at        timestamptz,
  source_type       varchar(24)  NOT NULL,
  source_id         uuid,
  created_at        timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_credit_lots_type CHECK (credit_type IN ('SEED','ALLOCATED','EARNED','PURCHASED')),
  CONSTRAINT ck_credit_lots_original CHECK (original_amount > 0),
  CONSTRAINT ck_credit_lots_remaining CHECK (remaining_amount BETWEEN 0 AND original_amount),
  CONSTRAINT ck_credit_lots_expiry CHECK ((expires_at IS NULL) = (credit_type IN ('EARNED','PURCHASED'))),
  CONSTRAINT ck_credit_lots_source CHECK (source_type IN ('POLICY','SESSION','PURCHASE','REFUND'))
);
CREATE INDEX ix_credit_lots_spendable ON wallet.credit_lots (tenant_id, account_id, expires_at) WHERE remaining_amount > 0;

CREATE TABLE wallet.ledger_entries (
  id               uuid         PRIMARY KEY,
  tenant_id        varchar(32)  NOT NULL,
  account_id       uuid         NOT NULL REFERENCES wallet.credit_accounts(id),
  lot_id           uuid         REFERENCES wallet.credit_lots(id),
  sequence_number  bigint       NOT NULL,
  direction        varchar(8)   NOT NULL,
  amount           integer      NOT NULL,
  reason           varchar(32)  NOT NULL,
  reference_type   varchar(24),
  reference_id     uuid,
  previous_hash    varchar(64),
  entry_hash       varchar(64)  NOT NULL,
  occurred_at      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_ledger_entries_sequence UNIQUE (tenant_id, sequence_number),
  CONSTRAINT ck_ledger_entries_amount CHECK (amount > 0),
  CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT','CREDIT')),
  CONSTRAINT ck_ledger_entries_reason CHECK (reason IN ('GRANT','BOOKING_CHARGE','BOOKING_REFUND','SESSION_EARNING','EXPIRY','PURCHASE','ADJUSTMENT')),
  CONSTRAINT ck_ledger_entries_reference CHECK (reference_type IN ('BOOKING','SESSION','PURCHASE','POLICY'))
);
CREATE INDEX ix_ledger_entries_account ON wallet.ledger_entries (tenant_id, account_id, occurred_at DESC);

-- The ledger is append only: corrections are new ADJUSTMENT entries.
CREATE FUNCTION wallet.reject_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'wallet.ledger_entries is append only';
END;
$$;
CREATE TRIGGER tr_ledger_entries_append_only
  BEFORE UPDATE OR DELETE ON wallet.ledger_entries
  FOR EACH ROW EXECUTE FUNCTION wallet.reject_ledger_change();
-- recognition: recognition requests and the university decision
CREATE SCHEMA IF NOT EXISTS recognition;

CREATE TABLE recognition.rules (
  id              uuid          PRIMARY KEY,
  tenant_id       varchar(32)   NOT NULL,
  minimum_hours   integer       NOT NULL,
  minimum_rating  numeric(3,2),
  valid_from      date          NOT NULL,
  superseded_at   timestamptz,
  created_at      timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_rules_minimum_hours CHECK (minimum_hours > 0)
);

CREATE TABLE recognition.requests (
  id               uuid           PRIMARY KEY,
  tenant_id        varchar(32)    NOT NULL,
  student_id       uuid           NOT NULL,
  total_hours      integer        NOT NULL,
  sessions_count   integer        NOT NULL,
  average_rating   numeric(3,2),
  status           varchar(16)    NOT NULL,
  reviewed_by      uuid,
  reviewed_at      timestamptz,
  decision_reason  varchar(1000),
  submitted_at     timestamptz    NOT NULL DEFAULT now(),
  CONSTRAINT ck_requests_status CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
  CONSTRAINT ck_requests_review CHECK (status IN ('SUBMITTED','UNDER_REVIEW') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX ix_requests_status ON recognition.requests (tenant_id, status);
CREATE INDEX ix_requests_student ON recognition.requests (tenant_id, student_id, submitted_at DESC);

CREATE TABLE recognition.request_sessions (
  request_id  uuid         NOT NULL REFERENCES recognition.requests(id),
  session_id  uuid         NOT NULL,
  tenant_id   varchar(32)  NOT NULL,
  hours       smallint     NOT NULL,
  CONSTRAINT pk_request_sessions PRIMARY KEY (request_id, session_id),
  CONSTRAINT uq_request_sessions_session UNIQUE (tenant_id, session_id)
);
-- reputation: ratings and tutor standing per skill
CREATE SCHEMA IF NOT EXISTS reputation;

CREATE TABLE reputation.ratings (
  id              uuid          PRIMARY KEY,
  tenant_id       varchar(32)   NOT NULL,
  session_id      uuid          NOT NULL,
  rated_by        uuid          NOT NULL,
  rated_user      uuid          NOT NULL,
  direction       varchar(24)   NOT NULL,
  stars           smallint,
  was_punctual    boolean,
  connection_ok   boolean,
  session_flowed  boolean,
  comment         varchar(500),
  created_at      timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_ratings_session_direction UNIQUE (session_id, direction),
  CONSTRAINT ck_ratings_direction CHECK (direction IN ('STUDENT_TO_TUTOR','TUTOR_TO_STUDENT')),
  CONSTRAINT ck_ratings_stars CHECK (stars IS NULL OR stars BETWEEN 1 AND 5),
  CONSTRAINT ck_ratings_stars_direction CHECK ((direction = 'STUDENT_TO_TUTOR') = (stars IS NOT NULL))
);

CREATE TABLE reputation.rating_tags (
  rating_id  uuid         NOT NULL REFERENCES reputation.ratings(id),
  tag        varchar(40)  NOT NULL,
  CONSTRAINT pk_rating_tags PRIMARY KEY (rating_id, tag)
);

CREATE TABLE reputation.tutor_standing (
  tenant_id        varchar(32)   NOT NULL,
  tutor_id         uuid          NOT NULL,
  catalog_item_id  uuid          NOT NULL,
  sessions_taught  integer       NOT NULL DEFAULT 0,
  ratings_count    integer       NOT NULL DEFAULT 0,
  average_stars    numeric(3,2),
  updated_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_tutor_standing PRIMARY KEY (tenant_id, tutor_id, catalog_item_id)
);
-- matching: read projection that makes tutor search fast
CREATE SCHEMA IF NOT EXISTS matching;

CREATE TABLE matching.available_offers (
  tenant_id        varchar(32)   NOT NULL,
  block_id         uuid          NOT NULL,
  tutor_id         uuid          NOT NULL,
  catalog_item_id  uuid          NOT NULL,
  starts_at        timestamptz   NOT NULL,
  tutor_name       varchar(160)  NOT NULL,
  average_stars    numeric(3,2),
  ratings_count    integer       NOT NULL DEFAULT 0,
  sessions_taught  integer       NOT NULL DEFAULT 0,
  CONSTRAINT pk_available_offers PRIMARY KEY (tenant_id, block_id, catalog_item_id)
);
CREATE INDEX ix_available_offers_search ON matching.available_offers (tenant_id, catalog_item_id, starts_at);
-- audit: append only activity log and detected anomalies
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.activity_log (
  id             uuid         PRIMARY KEY,
  tenant_id      varchar(32)  NOT NULL,
  actor_id       uuid,
  actor_role     varchar(16)  NOT NULL,
  action         varchar(64)  NOT NULL,
  resource_type  varchar(32)  NOT NULL,
  resource_id    uuid,
  metadata       jsonb,
  occurred_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_activity_log_recent ON audit.activity_log (tenant_id, occurred_at DESC);
CREATE INDEX ix_activity_log_resource ON audit.activity_log (tenant_id, resource_type, resource_id);

CREATE TABLE audit.anomalies (
  id           uuid         PRIMARY KEY,
  tenant_id    varchar(32)  NOT NULL,
  kind         varchar(32)  NOT NULL,
  subject_id   uuid         NOT NULL,
  severity     varchar(8)   NOT NULL,
  detail       jsonb,
  status       varchar(16)  NOT NULL,
  resolved_by  uuid,
  resolved_at  timestamptz,
  detected_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_anomalies_kind CHECK (kind IN ('REPEATED_PAIR','UNUSUAL_FREQUENCY','SHORT_DURATION','FAILED_PRESENCE','NO_BOARD_ACTIVITY')),
  CONSTRAINT ck_anomalies_severity CHECK (severity IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_anomalies_status CHECK (status IN ('OPEN','DISMISSED','CONFIRMED'))
);
CREATE INDEX ix_anomalies_queue ON audit.anomalies (tenant_id, status, severity);

CREATE TABLE audit.anomaly_sessions (
  anomaly_id  uuid         NOT NULL REFERENCES audit.anomalies(id),
  session_id  uuid         NOT NULL,
  tenant_id   varchar(32)  NOT NULL,
  CONSTRAINT pk_anomaly_sessions PRIMARY KEY (anomaly_id, session_id)
);
-- notifications: notices delivered to students, tutors and coordinators
CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE notifications.notifications (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32),
  recipient_id     uuid          NOT NULL,
  recipient_email  varchar(160)  NOT NULL,
  kind             varchar(40)   NOT NULL,
  payload          jsonb         NOT NULL,
  sent_at          timestamptz,
  failed_reason    varchar(500),
  read_at          timestamptz,
  created_at       timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_recipient ON notifications.notifications (tenant_id, recipient_id, created_at DESC);
CREATE INDEX ix_notifications_unsent ON notifications.notifications (kind) WHERE sent_at IS NULL;
-- payments: credit purchases
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE payments.purchases (
  id                  uuid           PRIMARY KEY,
  tenant_id           varchar(32)    NOT NULL,
  student_id          uuid           NOT NULL,
  credits             integer        NOT NULL,
  amount              numeric(10,2)  NOT NULL,
  currency            varchar(3)     NOT NULL DEFAULT 'PEN',
  status              varchar(16)    NOT NULL,
  provider_reference  varchar(128),
  idempotency_key     varchar(64)    NOT NULL UNIQUE,
  confirmed_at        timestamptz,
  created_at          timestamptz    NOT NULL DEFAULT now(),
  CONSTRAINT ck_purchases_amounts CHECK (credits > 0 AND amount > 0),
  CONSTRAINT ck_purchases_status CHECK (status IN ('PENDING','CONFIRMED','FAILED','EXPIRED'))
);
CREATE INDEX ix_purchases_student ON payments.purchases (tenant_id, student_id, created_at DESC);
-- analytics: usage indicators per university, aggregates only
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.daily_indicators (
  tenant_id           varchar(32)  NOT NULL,
  day                 date         NOT NULL,
  sessions_completed  integer      NOT NULL DEFAULT 0,
  hours_exchanged     integer      NOT NULL DEFAULT 0,
  active_students     integer      NOT NULL DEFAULT 0,
  new_students        integer      NOT NULL DEFAULT 0,
  requests_approved   integer      NOT NULL DEFAULT 0,
  CONSTRAINT pk_daily_indicators PRIMARY KEY (tenant_id, day)
);

CREATE TABLE analytics.uncovered_demand (
  tenant_id         varchar(32)  NOT NULL,
  catalog_item_id   uuid         NOT NULL,
  day               date         NOT NULL,
  searches          integer      NOT NULL DEFAULT 0,
  offers_available  integer      NOT NULL DEFAULT 0,
  CONSTRAINT pk_uncovered_demand PRIMARY KEY (tenant_id, catalog_item_id, day)
);
