# US27 - As a student in my last terms who needs extracurricular credits to graduate, I want to see
# how much I am missing to reach the recognition, so that I can count how many more tutoring
# sessions I have to teach before the term ends.
#
# Each scenario below is covered by a test method of the same name in
# RecognitionProgressAcceptanceTest, which exercises it over HTTP against a real PostgreSQL. The
# identity of each person and the sessions they taught are stubbed, since identity and sessions are
# other modules; the wallet and everything else are the real application.
#
# The hours are the booked hours of the verified sessions the student taught, which are the credits
# earned by teaching: one credit is one hour. Credits a university assigned and credits a student
# bought have no session behind them and never count.
#
# What the university asks for is its recognition rule. A university with no rule in force has not
# opened recognition: the progress shows the hours taught, no requirement, and no way to request.

Feature: Recognition progress

  Background:
    Given a student of the university "UPC"
    And the university asks for 20 hours to consider the recognition

  Scenario: Visible progress
    Given the student taught sessions adding up to 14 hours
    When the student reads their progress
    Then they see 14 hours earned, 20 required and 6 missing

  Scenario: Only earned credits count
    Given the student taught sessions adding up to 14 hours
    And the university assigned them 30 credits and they bought 10
    When the student reads their progress
    Then they see 14 hours earned and not 54

  Scenario: Update after each session
    Given the student taught sessions adding up to 14 hours
    When the student reads their progress
    And a session of 2 hours completes
    And the student reads their progress again
    Then they see 14 hours the first time and 16 the second

  Scenario: Goal reached
    Given the student taught sessions adding up to 20 hours
    When the student reads their progress
    Then they see nothing missing
    And they can request the recognition

  Scenario: A university that has not opened recognition
    Given the university has no recognition rule
    When the student reads their progress
    Then they see the hours taught, no requirement and no way to request

  Scenario: A person who is not of the university
    When someone unknown to the university reads their progress
    Then it is not found
