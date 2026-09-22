# US01 - As a student struggling in a course I want to see what tutoring blocks are available for
# that course in the times I can, so that I get help before my next assessment.
#
# The scenarios below are the acceptance criteria of the story. Each one is covered by a test
# method of the same name in SearchOffersAcceptanceTest, which exercises it over HTTP against a
# real PostgreSQL.
#
# Times are written as "in N hours" rather than as clock times on purpose: a test pinned to a
# specific hour starts failing on its own once that hour is in the past.

Feature: Searching for available tutoring offers

  Background:
    Given a student of the university "UPC"
    And a course "Calculus II" in the catalog

  Scenario: Search with results
    Given a tutor has a one hour block open for "Calculus II" starting in 2 hours
    When the student searches "Calculus II" for a window that includes that hour
    Then the search returns that block
    And the block shows its date, its starting time and the tutor offering it

  Scenario: Several tutors at the same hour
    Given two tutors have a one hour block open for "Calculus II" at the same time
    When the student searches "Calculus II" for a window that includes that hour
    Then the search returns both tutors
    And each tutor is shown with their rating
    And the student can tell them apart to choose which one to book with

  Scenario: No availability in the chosen window
    Given no tutor has a block open for "Calculus II" in the window the student searches
    But a tutor has a block open for "Calculus II" 3 hours after that window
    When the student searches "Calculus II" for that window
    Then the search does not return an empty result
    And the search returns the closest block outside the window instead

  Scenario: Times are shown in the university's own time zone
    Given a tutor has a one hour block open for "Calculus II" stored in UTC
    When the student searches "Calculus II"
    Then the block's time is shown in the time zone of the university "UPC"