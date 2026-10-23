# US19 - As a tutor, I want to publish the hours I am free every week, so that students can book
# the subjects I teach without offering hours I cannot honour.
#
# Scenarios 1, 3 and 5 are covered by TutorAvailabilityAcceptanceTest, over HTTP against a real
# PostgreSQL. Scenario 2 is matching's to answer: booking generates one block per hour, not per
# skill, and matching turns each block into one offer per enabled skill when it hears
# HoursGenerated. Scenario 4 needs confirmed bookings and a way to remove a window, neither of which
# is covered by AdjustAvailabilityAcceptanceTest along with the pause and date-exception updates.
#
# The last two scenarios read back what the tutor declared, which the agenda does not show: it
# lists the hours, not the rules they come from. They are run by
# TutorAvailabilityReadAcceptanceTest, signed in with real session tokens, with the same names.

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

  Scenario: Removing a weekly range keeps confirmed bookings and withdraws its other hours
    Given the tutor has a confirmed booking inside a weekly range
    When the tutor removes that range
    Then the confirmed booking stands and is reported
    And its other generated hours are withdrawn from the search

  Scenario: A tutor without enabled skills publishes no availability
    Given the tutor has no enabled skill
    When the tutor saves availability on tomorrow's weekday from 09:00 to 12:00
    Then the availability is saved but no blocks are generated
    And the answer carries a notice saying there is no enabled skill yet
    And HoursGenerated is not published

  Scenario: A tutor reads back the weekly ranges they declared
    Given the tutor has availability on tomorrow's weekday from 09:00 to 12:00
    And the tutor saved other weekly ranges, one of them with a last day still to come
    And the tutor removed one of their ranges
    And one of their ranges ended yesterday
    When the tutor reads back their availability
    Then the answer is in the university's time zone
    And it lists the ranges still in force, by day of the week and hour
    And neither the removed range nor the one that ended is listed

  Scenario: A tutor reads back only their own availability
    Given the tutor has availability, a date exception and a pause
    When another tutor of the same university reads back their availability
    Then nothing of the first tutor is listed
    When a student of another university reads back theirs
    Then nothing is listed
    And without a session the request is refused as unauthenticated
