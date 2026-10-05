-- Source of truth for reputation ratings.
CREATE TABLE reputation.ratings (
                                    id                uuid          PRIMARY KEY,
                                    tenant_id         varchar(32)   NOT NULL,
                                    session_id        uuid          NOT NULL,
                                    rated_by          uuid          NOT NULL,
                                    rated_user        uuid          NOT NULL,
                                    direction         varchar(24)   NOT NULL,
                                    stars             smallint,
                                    was_punctual      boolean,
                                    connection_ok     boolean,
                                    session_flowed    boolean,
                                    comment           varchar(500),
                                    created_at        timestamptz   NOT NULL DEFAULT now(),

                                    CONSTRAINT uq_ratings_session_direction
                                        UNIQUE (session_id, direction),

                                    CONSTRAINT ck_ratings_direction
                                        CHECK (direction IN ('STUDENT_TO_TUTOR', 'TUTOR_TO_STUDENT')),

                                    CONSTRAINT ck_ratings_stars
                                        CHECK (stars IS NULL OR stars BETWEEN 1 AND 5),

                                    CONSTRAINT ck_ratings_stars_direction
                                        CHECK ((direction = 'STUDENT_TO_TUTOR') = (stars IS NOT NULL))
);

CREATE TABLE reputation.rating_tags (
                                        rating_id uuid        NOT NULL REFERENCES reputation.ratings(id),
                                        tag       varchar(40) NOT NULL,

                                        PRIMARY KEY (rating_id, tag)
);

-- Internal context opened when reputation receives SessionCompleted.
-- It lets reputation validate who may rate the completed session and
-- preserves the catalogue item needed for per-skill standing.
CREATE TABLE reputation.rating_windows (
                                           tenant_id       varchar(32) NOT NULL,
                                           session_id      uuid        NOT NULL,
                                           tutor_id        uuid        NOT NULL,
                                           student_id      uuid        NOT NULL,
                                           catalog_item_id uuid        NOT NULL,
                                           opened_at       timestamptz NOT NULL,

                                           PRIMARY KEY (tenant_id, session_id)
);

COMMENT ON TABLE reputation.ratings IS
    'Ratings submitted by the participants of completed sessions.';

COMMENT ON TABLE reputation.rating_tags IS
    'Tags selected by a student when rating a tutoring session.';

COMMENT ON TABLE reputation.rating_windows IS
    'Completed sessions currently available to reputation for participant ratings.';