# Demo data - whoever opens the web application against the demo profile finds something to see on
# every screen: active students with different balances, tutors with different weeks, tutorings
# ahead, a cancelled one, and four weeks of history played with the real use cases.
#
# This is not a story of the backlog, so it carries no US number: it is the data the project is
# presented with (docs/demo.md, "The universities and their people" and "The history"). Nothing of
# it exists in a deployment.
#
# Every scenario names the test that runs it, with the same name: the application started with the
# demo profile alone, against a real PostgreSQL, in DemoSeedAcceptanceTest.

Feature: Demo data for every screen

  Background:
    Given the application started with the demo profile

  Scenario: The people of the demonstration are active with their balances
    Then each university has students at every step of the onboarding
    And students with no credits, one credit, five and fifteen, one with credits of three origins and one whose credits expire in two days

  Scenario: The search finds several courses and several tutors, and nobody for some courses
    When a student looks for the courses the tutors teach in the week ahead
    Then there are offers of several courses by at least three tutors
    And a course nobody teaches answers no offer at all

  Scenario: A student has a tutoring ahead and one cancelled
    Then the student has a confirmed tutoring between two and eight days ahead
    And a cancelled one whose credit came back

  Scenario: The history has completed and rated sessions
    Then each university has completed sessions with ratings, a whiteboard with content and one abandoned for each absence
    And recognition requests pending and approved, and notices read and unread

  Scenario: The rest of the students of the documents have no account
    Then only the people of the demonstration and the coordinators have an account

  Scenario: The ledger and the audit are intact and the clock is where it was
    Then the ledger and the activity log of each university are intact
    And the clock reads the real time

  Scenario: A second start duplicates nothing
    When the demonstration data runs again
    Then no row is added
