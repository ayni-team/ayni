# US28 - As a student who reached the required hours, I want to send my university a recognition
# request backed by the evidence of my tutoring sessions, so that it evaluates granting me the
# extracurricular credit.
#
# Each scenario below is covered by a test method of the same name in
# SubmitRecognitionRequestAcceptanceTest, which exercises it over HTTP against a real PostgreSQL. The
# identity of each person, the sessions they taught and their bookings are stubbed, since identity,
# sessions and booking are other modules; the wallet and everything else are the real application.
#
# A request is made of sessions, not of credits: it takes the oldest verified sessions that no request
# has used, until the hours the university asks for are reached, and the student keeps the rest for the
# next request. The figures are copied when the request is submitted.
#
# Ratings: reputation does not hold a rating per session yet, so the ratings of a request come from a
# port that has none today. The scenario that shows them replaces that port with one that has some.
#
# The scenarios after the fifth are not on the card. They pin down what it leaves out: a university
# that has not opened recognition, and a person who is not of the university.

Feature: Submitting a recognition request

  Background:
    Given a student of the university "UPC"
    And the university asks for 20 hours to consider the recognition

  Scenario: Sending the request
    Given the student taught sessions of 10, 10 and 5 hours, rated 5 and 4 stars
    When the student submits the request
    Then it is registered with 20 hours, the two oldest sessions and an average of 4.5
    And the university is told about it

  Scenario: Credits are not consumed
    Given the student earned 25 credits by teaching sessions of 10, 10 and 5 hours
    When the student submits the request
    Then their balance still has 25 credits available

  Scenario: The same hours are not presented twice
    Given the student taught three sessions of 12 hours
    And a request already backed by the first two
    When the student submits a new request
    Then it is refused because only 12 hours are free, and the used sessions are not offered again

  Scenario: Insufficient hours
    Given the student taught sessions adding up to 16 hours
    When the student tries to submit the request
    Then nothing is registered and they are told 4 hours are missing

  Scenario: Following the request
    Given the student submitted a request
    When they look at their requests
    Then they see it as submitted, with its figures and sessions
    And once the university resolved it they see the decision and the reason

  Scenario: A university that has not opened recognition
    Given the university has no recognition rule
    When the student tries to submit the request
    Then it is refused and nothing is registered

  Scenario: A person who is not of the university
    When someone unknown to the university submits or lists requests
    Then it is not found
