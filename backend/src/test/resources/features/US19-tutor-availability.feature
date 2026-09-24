# US19 - As a tutor, I want to publish the hours I am free every week, so that students can book
# the subjects I teach without offering hours I cannot honour.
#
# Scenarios 1, 3 and 5 are covered by TutorAvailabilityAcceptanceTest, over HTTP against a real
# PostgreSQL. Scenario 2 is matching's to answer: booking generates one block per hour, not per
# skill, and matching turns each block into one offer per enabled skill when it hears
# HoursGenerated. Scenario 4 needs confirmed bookings and a way to remove a window, neither of which
# exists yet: it is kept here as the criterion US03 and US05 must meet, tagged @pending.

Feature: Publishing the hours a tutor is free every week

  Background:
    Given a tutor of the university "UPC", whose time zone is America/Lima

  Scenario: Recurring availability is published
    Given the tutor has an enabled skill
    When the tutor saves availability on tomorrow's weekday from 09:00 to 12:00
    Then one-hour blocks are generated for that weekday over the coming four weeks
    And the answer says twelve hours were generated
    And HoursGenerated is published so that the hours appear in searches

  Scenario: Availability is valid for all my enabled skills
    Given the tutor has the enabled skills "Calculus I" and "Physics I"
    When the tutor saves availability on Monday from 09:00 to 10:00
    Then the generated hour is offered for "Calculus I" and for "Physics I"

  Scenario: Overlapping ranges are rejected
    Given the tutor has availability on tomorrow's weekday from 09:00 to 12:00
    When the tutor saves availability on the same weekday from 11:00 to 13:00
    Then the new availability is rejected with a message saying it overlaps
    And no duplicate blocks are generated

  @pending
  Scenario: Reducing availability with confirmed bookings
    Given the tutor has a confirmed booking inside a weekly range
    When the tutor removes that range
    Then the confirmed booking stands
    And no new blocks are generated for that range

  Scenario: A tutor without enabled skills publishes no availability
    Given the tutor has no enabled skill
    When the tutor saves availability on tomorrow's weekday from 09:00 to 12:00
    Then the availability is saved but no blocks are generated
    And the answer carries a notice saying there is no enabled skill yet
    And HoursGenerated is not published
