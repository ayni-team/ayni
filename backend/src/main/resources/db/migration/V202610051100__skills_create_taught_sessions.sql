-- =============================================================================
-- Skills: the sessions that were taught on each catalogue item (US44)
-- =============================================================================
-- A moderator who reviews the catalogue decides by use: how many tutors offer an item and how many
-- sessions were taught on it. The first is skills' own data. The second belongs to sessions, which
-- announces each verified session with SessionCompleted and keeps no count per skill, so skills
-- keeps the one thing it needs from those announcements: that session X was taught on item Y.
--
-- Skills cannot ask reputation, which does keep a count, because reputation already depends on
-- skills and the modules would depend on each other.
--
-- One row per session, keyed by the session, so that an event delivered twice counts once. It is a
-- projection: it is rebuilt from the events and nothing else writes it. It starts empty: the
-- sessions completed before this table existed are not counted.
--
-- catalog_item_id has no foreign key on purpose. The row records what another module announced, and
-- a listener that failed on an item it does not know would leave the announcement unprocessed
-- without anyone being able to fix it. An unknown item is simply never read.
--
-- The schema itself is created by V1.
-- =============================================================================

CREATE TABLE skills.taught_sessions (
  session_id       uuid         PRIMARY KEY,
  tenant_id        varchar(32)  NOT NULL,
  catalog_item_id  uuid         NOT NULL,
  tutor_id         uuid         NOT NULL,
  completed_at     timestamptz  NOT NULL
);

-- What the moderator reads: how many sessions an item has, whatever university taught them.
CREATE INDEX ix_taught_sessions_item
  ON skills.taught_sessions (catalog_item_id);
