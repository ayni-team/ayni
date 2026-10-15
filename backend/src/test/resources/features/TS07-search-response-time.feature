# TS07 - As a student looking for help with a course, I need the results to appear without a
# noticeable wait, so that I do not give up the search before finding a tutor.
#
# Each scenario runs with the same name in SearchResponseTimeTest: real HTTP to the running
# application with real session tokens (TS03), as ayni_app with row level security on (TS01), and
# a PostgreSQL projection of five universities of 320 courses and 120 tutors each. The fifty
# students search once for the whole class and each scenario judges what was measured. The method
# and the figures are in docs/performance/TS07-search-response-time.md.

Feature: Response time of the search

  Background:
    Given five universities with more than three hundred courses and a hundred active tutors each

  Scenario: Response time under normal use
    Given fifty students using the platform at the same time
    When they search for tutorings
    Then 95 % of the answers arrive in less than 500 milliseconds

  Scenario: Response time in the worst case
    Given the same concurrency
    When the whole set of answers is measured
    Then none of them takes more than 2 seconds

  Scenario: Behaviour with a wide catalogue
    Given a university with more than three hundred courses and a hundred active tutors
    When a search runs, whether it finds hours in the window, only near it or none
    Then the response time stays within the same limits
    And every query of the search reads the search index and never the whole table

  Scenario: Reproducible measurement
    Given the compliance has to be shown
    When the load test defined for these scenarios runs
    Then it gives a report of the times measured, over the same data, that can be repeated
