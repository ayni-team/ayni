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
