-- =============================================================================
-- Identity: universities, users, access links, sessions and academic profiles
-- =============================================================================
-- Identity owns the institutional sign-in flow and the academic identity of
-- students and coordinators.
--
-- The schema itself already exists from V1. Every tenant-scoped table carries
-- tenant_id and application queries must always filter by it.
-- =============================================================================

CREATE TABLE identity.tenants (
                                  id                       uuid          PRIMARY KEY,
                                  code                     varchar(32)   NOT NULL UNIQUE,
                                  name                     varchar(160)  NOT NULL,
                                  logo_url                 varchar(512),
                                  primary_color            varchar(16),
                                  secondary_color          varchar(16),
                                  email_domains            text[]        NOT NULL DEFAULT '{}',
                                  minimum_teaching_grade   numeric(4,2)  NOT NULL,
                                  timezone                 varchar(64)   NOT NULL DEFAULT 'America/Lima',
                                  status                   varchar(16)   NOT NULL,
                                  created_at               timestamptz   NOT NULL DEFAULT now(),
                                  updated_at               timestamptz   NOT NULL DEFAULT now(),

                                  CONSTRAINT ck_identity_tenants_status
                                      CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX ix_identity_tenants_email_domains
    ON identity.tenants USING gin (email_domains);


-- -----------------------------------------------------------------------------
-- Platform administrators are deliberately outside the tenant user table.
-- They manage universities but must not gain access to student academic data.
-- -----------------------------------------------------------------------------

CREATE TABLE identity.platform_admins (
                                          id           uuid          PRIMARY KEY,
                                          email        varchar(160)  NOT NULL UNIQUE,
                                          full_name    varchar(160)  NOT NULL,
                                          status       varchar(16)   NOT NULL,
                                          created_at   timestamptz   NOT NULL DEFAULT now(),

                                          CONSTRAINT ck_identity_platform_admins_status
                                              CHECK (status IN ('ACTIVE', 'DISABLED'))
);


-- -----------------------------------------------------------------------------
-- Students and coordinators belong to exactly one university.
-- -----------------------------------------------------------------------------

CREATE TABLE identity.users (
                                id                  uuid          PRIMARY KEY,
                                tenant_id           varchar(32)   NOT NULL,
                                role                varchar(16)   NOT NULL,
                                email               varchar(160)  NOT NULL,
                                student_code        varchar(32),
                                full_name           varchar(160)  NOT NULL,
                                career              varchar(160),
                                current_term        varchar(16),
                                photo_url           varchar(512),
                                bio                 varchar(500),
                                status              varchar(16)   NOT NULL,
                                onboarding_step     varchar(32),
                                activated_at        timestamptz,
                                created_at          timestamptz   NOT NULL DEFAULT now(),
                                updated_at          timestamptz   NOT NULL DEFAULT now(),

                                CONSTRAINT uq_identity_users_email
                                    UNIQUE (tenant_id, email),

                                CONSTRAINT uq_identity_users_student_code
                                    UNIQUE (tenant_id, student_code),

                                CONSTRAINT ck_identity_users_role
                                    CHECK (role IN ('STUDENT', 'COORDINATOR')),

                                CONSTRAINT ck_identity_users_status
                                    CHECK (status IN ('PENDING', 'ACTIVE', 'RESTRICTED')),

                                CONSTRAINT ck_identity_users_student_code
                                    CHECK (role = 'COORDINATOR' OR student_code IS NOT NULL)
);


-- -----------------------------------------------------------------------------
-- Single-use institutional access links.
-- Only the token hash is persisted.
-- -----------------------------------------------------------------------------

CREATE TABLE identity.access_links (
                                       id             uuid          PRIMARY KEY,
                                       tenant_id      varchar(32),
                                       email          varchar(160)  NOT NULL,
                                       purpose        varchar(24)   NOT NULL,
                                       token_hash     varchar(64)   NOT NULL UNIQUE,
                                       expires_at     timestamptz   NOT NULL,
                                       consumed_at    timestamptz,
                                       requested_ip   varchar(45),
                                       created_at     timestamptz   NOT NULL DEFAULT now(),

                                       CONSTRAINT ck_identity_access_links_purpose
                                           CHECK (purpose IN ('ACTIVATION', 'LOGIN', 'COORDINATOR_INVITE'))
);

CREATE INDEX ix_identity_access_links_pending
    ON identity.access_links (email, purpose)
    WHERE consumed_at IS NULL;


-- -----------------------------------------------------------------------------
-- Long-lived sessions created after a magic link is consumed.
-- -----------------------------------------------------------------------------

CREATE TABLE identity.user_sessions (
                                        id            uuid          PRIMARY KEY,
                                        tenant_id     varchar(32),
                                        user_id       uuid,
                                        admin_id      uuid,
                                        token_hash    varchar(64)   NOT NULL UNIQUE,
                                        expires_at    timestamptz   NOT NULL,
                                        revoked_at    timestamptz,
                                        created_at    timestamptz   NOT NULL DEFAULT now(),

                                        CONSTRAINT ck_identity_user_sessions_subject
                                            CHECK (num_nonnulls(user_id, admin_id) = 1)
);


-- -----------------------------------------------------------------------------
-- Copy of what the university academic system reports.
-- It is refreshed from that system and never edited by Ayni.
-- -----------------------------------------------------------------------------

CREATE TABLE identity.academic_records (
                                           id            uuid          PRIMARY KEY,
                                           tenant_id     varchar(32)   NOT NULL,
                                           user_id       uuid          NOT NULL REFERENCES identity.users(id),
                                           course_code   varchar(32)   NOT NULL,
                                           course_name   varchar(160)  NOT NULL,
                                           grade         numeric(4,2)  NOT NULL,
                                           term          varchar(16)   NOT NULL,
                                           synced_at     timestamptz   NOT NULL DEFAULT now(),

                                           CONSTRAINT uq_identity_academic_records
                                               UNIQUE (tenant_id, user_id, course_code, term)
);

CREATE INDEX ix_identity_academic_records_user
    ON identity.academic_records (tenant_id, user_id);


-- -----------------------------------------------------------------------------
-- University credit policies are immutable. A new one supersedes the old one.
-- -----------------------------------------------------------------------------

CREATE TABLE identity.credit_policies (
                                          id                  uuid          PRIMARY KEY,
                                          tenant_id           varchar(32)   NOT NULL,
                                          kind                varchar(16)   NOT NULL,
                                          credits_amount      integer       NOT NULL,
                                          validity_days       integer       NOT NULL,
                                          target_rule         jsonb,
                                          valid_from          date          NOT NULL,
                                          superseded_at       timestamptz,
                                          created_by_admin    uuid,
                                          created_by_user     uuid,
                                          created_at          timestamptz   NOT NULL DEFAULT now(),

                                          CONSTRAINT ck_identity_credit_policies_kind
                                              CHECK (kind IN ('BASELINE', 'TARGETED')),

                                          CONSTRAINT ck_identity_credit_policies_values
                                              CHECK (credits_amount > 0 AND validity_days > 0),

                                          CONSTRAINT ck_identity_credit_policies_author
                                              CHECK (num_nonnulls(created_by_admin, created_by_user) = 1)
);

CREATE INDEX ix_identity_credit_policies_current
    ON identity.credit_policies (tenant_id, kind)
    WHERE superseded_at IS NULL;