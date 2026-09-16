-- =============================================================================
-- V1 - One database schema per application module
-- =============================================================================
-- Each module owns its own schema and nothing else writes into it. A module that
-- needs to reference data owned by another one stores the identifier and asks
-- that module for the rest: foreign keys never cross a schema boundary.
--
-- That single rule is what lets a module be extracted into its own service later
-- as a data migration rather than a redesign.
--
-- Tables are created by later migrations, one file per change, never by editing
-- this one: Flyway records which files it already applied.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS skills;
CREATE SCHEMA IF NOT EXISTS booking;
CREATE SCHEMA IF NOT EXISTS matching;
CREATE SCHEMA IF NOT EXISTS sessions;
CREATE SCHEMA IF NOT EXISTS wallet;
CREATE SCHEMA IF NOT EXISTS recognition;
CREATE SCHEMA IF NOT EXISTS reputation;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS notifications;

COMMENT ON SCHEMA identity      IS 'Universities, students, coordinators and academic profiles';
COMMENT ON SCHEMA skills        IS 'Catalogue of courses and tools, offered skills and accreditations';
COMMENT ON SCHEMA booking       IS 'Availability, hour blocks and reservations';
COMMENT ON SCHEMA matching      IS 'Read projection that makes tutor search fast';
COMMENT ON SCHEMA sessions      IS 'Sessions, attendance, presence checks and whiteboards';
COMMENT ON SCHEMA wallet        IS 'Credit accounts, credit groups and ledger entries';
COMMENT ON SCHEMA recognition   IS 'Recognition requests and the university decision';
COMMENT ON SCHEMA reputation    IS 'Ratings and tutor standing per skill';
COMMENT ON SCHEMA payments      IS 'Credit purchases';
COMMENT ON SCHEMA audit         IS 'Append only activity log and detected anomalies';
COMMENT ON SCHEMA analytics     IS 'Usage indicators per university';
COMMENT ON SCHEMA notifications IS 'Notices delivered to students, tutors and coordinators';
