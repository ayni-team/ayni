# US20 - As a tutor, I want to adjust my availability on a specific date, adding or removing hours,
# so that my offer is right when my week is not the usual one.
#
# Every scenario names the test that runs it, against a real PostgreSQL, in
# AdjustAvailabilityAcceptanceTest, with the same name. The entity rules are covered by
# AvailabilityExceptionTest and BlockGeneratorTest, and withdrawing one hour by HourBlockTest.
#
# Hours are generated weeks ahead, so a date is usually changed after its hours exist. The change
# reaches them at once: removed hours leave the search, added hours can be booked.

Feature: Adjusting availability on a specific date

  Background:
    Given a tutor of the university "UPC", enabled for "Databases I"
    And the tutor is free tomorrow from 09:00 to 12:00, Lima time, with those hours already generated

  Scenario: Removing a window withdraws only the hours it covers
    When the tutor removes tomorrow from 10:00 to 11:00
    Then the hour at 10:00 is withdrawn and leaves the search
    And the hours at 09:00 and 11:00 are still offered
    And removing the same window again withdraws nothing more

  Scenario: Removing a whole day withdraws all of its hours
    When the tutor removes the whole of tomorrow
    Then the three hours of tomorrow are withdrawn

  Scenario: Adding hours to a date makes them bookable at once
    When the tutor adds tomorrow from 14:00 to 16:00
    Then the hours at 14:00 and 15:00 exist at once and are in the search
    And a student can hold them
