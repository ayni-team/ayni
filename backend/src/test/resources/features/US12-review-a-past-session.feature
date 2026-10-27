# US12 - As a participant, I want to open a session of my history and see what we worked on, so that
# I can review it.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# ReviewPastSessionAcceptanceTest. The real duration is a rule of the session, in SessionTest.

Feature: Reviewing a past session

  Scenario: A participant reviews a session they took
    Given a session that took place, with things drawn on its board and support material
    When one of its participants opens it from their history
    Then they see the board as it was left, the support material, the need and the real duration

  Scenario: A cancelled session shows that it was cancelled
    Given a session whose booking was cancelled before it started
    When one of its participants opens it
    Then they see that it was cancelled, and no empty board

  Scenario: A session that did not start shows why
    Given a session abandoned because the tutor did not come
    When the student opens it
    Then they see that it did not start and who did not come, and no board

  Scenario: Somebody who did not take part is refused
    Given a session of two other students
    When somebody else opens it, from the same university or from another one
    Then the first is refused and the second does not find it

  # Not a scenario of the story: a session where nothing was drawn.
  Scenario: A session nobody drew on shows an empty board marked without activity
    Given a session that took place with nothing drawn
    When a participant opens it
    Then the board is empty and says there was no activity
