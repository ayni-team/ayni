# US07 - As a tutor, I want support material prepared from what the student needs, so that I arrive
# to the session prepared.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# SupportMaterialAcceptanceTest, except the one about a service that fails, which runs in
# SupportMaterialUnavailableAcceptanceTest with the simulated service answering that it is not
# available. The sessions are created the real way, by publishing BookingConfirmed. The rules of the
# material are in SupportMaterialTest and the simulated service in SimulatedSupportMaterialGeneratorTest.

Feature: Support material for the session

  Background:
    Given a student booked an hour and wrote what they need help with

  Scenario: The material is prepared when the booking is confirmed
    When the booking is confirmed
    Then its session has support material made from the need: a summary and suggestions

  Scenario: The tutor reads the material and the need before the session
    Given the session of a confirmed booking has its support material
    When the tutor reads the session
    Then they see what the student needs and the material prepared from it

  Scenario: When the service fails the session shows only the need
    Given the service that prepares the material is not available
    When the booking is confirmed
    Then the session is scheduled all the same
    And reading it shows the need and no material

  # Not a scenario of the story: the confirmation is delivered at least once.
  Scenario: The same confirmation heard twice prepares the material once
    When the confirmation of the booking arrives twice
    Then the session has one support material
