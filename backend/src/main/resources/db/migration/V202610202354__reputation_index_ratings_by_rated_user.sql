-- =============================================================================
-- V202610202354 - Reputation: find the ratings a person received
-- =============================================================================
-- US33 counts the tags students gave a tutor, per skill. The ratings of one
-- tutor are found by who was rated and in which direction; the unique key of
-- the table, the session and the direction, cannot find them.
-- =============================================================================

CREATE INDEX ix_ratings_rated_user
    ON reputation.ratings (tenant_id, rated_user, direction);
