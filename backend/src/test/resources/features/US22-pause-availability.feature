# US22 - As a tutor, I want to pause my availability for a period, so that nobody books me while I
# am away.
#
# Every scenario names the test that runs it, against a real PostgreSQL, in
# AdjustAvailabilityAcceptanceTest, with the same name. The pause itself is covered by
# AvailabilityPauseTest, generating no hours inside a pause by BlockGeneratorTest, and withdrawing
# one hour by HourBlockTest.
#
# A booked hour stands through a pause, as US19 scenario 4 says of removing availability: only
# cancelling undoes a booking, and cancelling refunds (US05).

Feature: Pausing availability

  Background:
    Given a tutor of the university "UPC", enabled for "Databases I"
    And the tutor is free tomorrow from 09:00 to 12:00, Lima time, with those hours already generated

  Scenario: Pausing withdraws the hours that already existed
    When the tutor pauses tomorrow
    Then the three hours of tomorrow are withdrawn and leave the search
    And HoursWithdrawn is published with those hours
    And a student cannot hold them any more

  Scenario: A booked hour stands through a pause
    Given Ana booked tomorrow's hour at 09:00
    When the tutor pauses tomorrow
    Then the hour at 09:00 is still booked and Ana's credits are still charged
    And the other two hours are withdrawn
    And the tutor is told that one booked hour stands

  Scenario: An hour a student is holding is withdrawn, and confirming it is refused
    Given Ana holds tomorrow's hour at 10:00
    When the tutor pauses tomorrow
    Then the hour is withdrawn
    And Ana's confirmation is refused because the hour is no longer offered, charging nothing

  Scenario: A pause beyond the generated weeks changes no hours now
    When the tutor pauses a week two months ahead
    Then no hour is withdrawn now, because none exists yet for those days
    # When the nightly job reaches them it generates nothing for the pause: BlockGeneratorTest.
