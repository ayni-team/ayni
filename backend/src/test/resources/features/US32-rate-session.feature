# US32 - As a student who has just finished a tutoring session, I want to rate how it went and say
# what stood out of the tutor, so that the next students choose with real information.
# US34 - As a tutor who gave a session, I want to leave a record of how it went on the student's
# side, so that those who make use of tutoring are told apart from those who waste the time.
#
# Both stories are one endpoint, POST /api/v1/sessions/{id}/rating. The student sends stars and
# tags; the tutor sends three facts, punctual, connection held and session flowed, which is
# deliberately not an opinion about the person. The window to rate opens when reputation hears
# SessionCompleted, so a session that did not complete cannot be rated.
#
# What a participant registered is read back with GET /api/v1/sessions/{id}/rating, which answers
# only the caller's own direction: the student reads their stars and tags, the tutor their three
# facts. A second POST still answers a conflict; the client reads the GET to show what was
# registered. Neither the tutor nor the student reads what the other left, and nothing says who
# rated: the tutor learns their stars and tags only through GET /api/v1/tutor/reputation (US33),
# counted per skill.
#
# Each scenario that is not tagged @pending is covered by a test method with the same display name:
# RateSessionAcceptanceTest runs the story scenarios that register a rating over HTTP against a real
# PostgreSQL, one test per scenario; RegisteredRatingAcceptanceTest runs those that read one back,
# signed in with real session tokens (TS03); RateSessionConcurrencyTest and
# RecordCompletedSessionIdempotencyTest run the last three, which are not on the cards.
#
# Two scenarios stay @pending because reputation is not where they are built: offering the rating
# when the session closes is the client's, and the student's absences before a session are
# booking's.

Feature: Rating a tutoring session

  Background:
    Given a tutor and a student of the university "UPC"
    And a session of "Calculus I" between them that completed

  # US32 - the student rates the tutor

  Scenario: A student rates the tutoring they received
    When the student rates the session with 5 stars and the tags "CLEAR" and "HELPFUL"
    Then the rating is registered for that session
    And it counts in the tutor's standing in "Calculus I"

  @pending
  Scenario: A student is offered the rating when the session closes
    Given the session has just closed
    When the student confirms its end
    Then the system offers to rate it then, and the student can also do it later from the history

  Scenario: A second rating of the same session is refused
    Given the student already rated the session
    When the student rates it again
    Then the answer is a conflict and the tutor's standing does not change

  Scenario: A student sees the rating they registered
    Given the student already rated the session
    When the student tries to rate it again
    Then the system shows the rating that was registered instead of a new one

  Scenario: A session that did not complete cannot be rated
    Given a session that was cancelled or never started
    When the student tries to rate it
    Then the answer is not found

  Scenario: The tutor does not learn who rated them
    Given the student rated the session
    When the tutor looks at the rating
    Then they see the stars and the tags without who left them

  # US34 - the tutor assesses the student

  Scenario: A tutor assesses the student after the session
    When the tutor says the student was punctual, the connection held and the session flowed
    Then the assessment is registered for that session
    And it does not touch the tutor's own standing

  Scenario: The student does not see the assessment of the tutor
    Given the tutor assessed the student
    When the student looks at the session
    Then they do not see what the tutor left about them

  Scenario: A second assessment of the same session is refused
    Given the tutor already assessed the student
    When the tutor assesses them again
    Then the answer is a conflict

  Scenario: A tutor sees the assessment they registered
    Given the tutor already assessed the student
    When the tutor tries to assess them again
    Then the system shows the assessment that was registered instead of a new one

  Scenario: A tutor sees the student's absences before the session
    Given a student with registered absences books the tutor
    When the tutor reviews the session in their agenda
    Then they see the absences before preparing it

  # Not on the cards: what the endpoint refuses and what must hold when requests race.

  Scenario: Somebody who was not in the session cannot rate it
    When a person who is neither the tutor nor the student rates it
    Then the answer is forbidden and nothing is registered

  Scenario: A rating without stars from the student is refused
    When the student sends a rating without stars
    Then the answer is a bad request

  Scenario: An assessment missing one of the three facts is refused
    When the tutor sends an assessment without whether the connection held
    Then the answer is a bad request

  Scenario: Another university's session is not found
    Given the session completed at the university "PUCP"
    When the student of "UPC" rates it
    Then the answer is not found, never forbidden

  Scenario: A participant who has not rated yet reads that nothing was registered
    When the student and the tutor look at the session before rating it
    Then each reads that they have not rated it

  Scenario: Somebody who was not in the session cannot read its rating
    Given the student rated the session
    When a person who is neither the tutor nor the student looks at its rating
    Then the answer is forbidden

  Scenario: A session that did not complete has no rating to read
    When the student looks at the rating of a session that never completed
    Then the answer is not found

  Scenario: Another university's session is not found when reading its rating
    Given the session completed and was rated at another university
    When a student of "UPC" looks at its rating
    Then the answer is not found, never forbidden

  Scenario: Two ratings of the same session at once end with one rating and one refusal
    When the student sends the same rating twice at the same moment
    Then one is registered and the other answers a conflict, never a server error

  Scenario: Two students rating the same tutor at once are both counted
    Given two completed sessions of the tutor in "Calculus I" with different students
    When both students rate at the same moment
    Then the standing counts both ratings and averages them

  Scenario: A session completed twice is counted once
    When the completion of the session is delivered twice
    Then the tutor has taught it once and its rating window was opened once
