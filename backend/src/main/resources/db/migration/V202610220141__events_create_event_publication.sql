-- =============================================================================
-- TS04 - Reliable delivery of events: the event publication registry
-- =============================================================================
-- Until now every event travelled in memory: a listener that threw, or a
-- process that stopped between the commit and the listener, lost it without a
-- retry and without a trace (ADR 0005). From here on Spring Modulith stores one
-- row per event and per listener, in the same transaction as the change that
-- produced the event, and removes it once that listener completes
-- (spring.modulith.events.completion-mode=delete). A row that stays is a
-- delivery still owed, and it is delivered again on restart and by the
-- periodic resubmission of pe.ayni.config.
--
-- The table is exactly the one the library defines for PostgreSQL, copied from
-- schema-postgresql.sql of spring-modulith-events-jdbc 1.3.1, the version the
-- BOM pins, with its two indexes: Hibernate validates the entity of
-- spring-modulith-events-jpa against it. Only the schema is written out.
--
-- It lives in public, next to flyway_schema_history, and not in one of the
-- twelve schemas: it belongs to no module, its entity names no schema, so
-- Hibernate looks for it in the schema of the connection. It has no tenant_id,
-- so it needs no row level security policy: each listener binds the university
-- of its event when it runs. RowLevelSecurityPolicyTest does not read public.
--
-- serialized_event never holds an access link, a presence code or an email
-- address: config.RedactingEventSerializer leaves them out of AccessRequested,
-- PresenceCodeIssued and StudentActivated before they are stored.
-- =============================================================================

CREATE TABLE public.event_publication
(
  id               UUID NOT NULL,
  listener_id      TEXT NOT NULL,
  event_type       TEXT NOT NULL,
  serialized_event TEXT NOT NULL,
  publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date  TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx
    ON public.event_publication USING hash (serialized_event);

CREATE INDEX event_publication_by_completion_date_idx
    ON public.event_publication (completion_date);

-- The application stores, completes (deletes) and reads back the publications.
-- USAGE on public is granted to everybody by default; it is repeated so that a
-- database where it was revoked still works.
GRANT USAGE ON SCHEMA public TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON public.event_publication TO ${app_user};
