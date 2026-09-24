# US03 - As a student, I want to book a block of tutoring with a tutor, so that I get help with a
# subject at an hour that suits us both, paying for it with my credits.
#
# Every scenario names the test that runs it, over HTTP against a real PostgreSQL. A scenario
# nobody runs yet is tagged @pending and says why.
#
#   Block taken seconds earlier (hold)   BookABlockAcceptanceTest.anHourAnotherStudentHoldsCannotBeTaken
#                                        HoldRaceTest.exactlyOneStudentHoldsTheHour
#   Temporary hold expiry                BookABlockAcceptanceTest.anUnconfirmedHoldExpiresAfterFiveMinutes
#                                        ExpiredHoldsJobTest.freesTheExpiredHoldsOfEveryUniversity
#   Leaving the confirmation             BookABlockAcceptanceTest.leavingTheConfirmationGivesTheHoursBack
#
# The scenarios about confirming the booking arrive with POST /api/v1/bookings (US03-T4).

Feature: Booking a block of tutoring

  Background:
    Given a tutor of the university "UPC", enabled for "Databases I"
    And the tutor is free tomorrow from 09:00 to 12:00, Lima time
    And the students Ana and Bruno

  @pending
  Scenario: Successful booking
    Given Ana holds tomorrow's hours from 09:00 to 11:00
    When Ana confirms the booking describing what she needs
    Then two credits are deducted from her balance
    And both hours are booked and nobody else can hold them
    And BookingConfirmed is published with both blocks

  @pending
  Scenario: The booking creates the session
    Given Ana confirmed a booking
    Then a scheduled session exists for that booking, with a room nobody can guess

  @pending
  Scenario: Receiving the access link
    Given Ana confirmed a booking
    Then she receives the link to the session room
    # Not in this story: the link is delivered by notifications, which does not exist yet, and by
    # the participant endpoints of sessions (GET /api/v1/sessions/{id}, POST .../join).

  @pending
  Scenario: Credits closest to expiring are spent first
    Given Ana has 2 SEED credits expiring in 10 days, 5 ALLOCATED credits expiring in 60 days and 3 EARNED credits
    When Ana books three hours
    Then the 2 SEED credits and 1 ALLOCATED credit are spent
    And her EARNED credits are untouched

  @pending
  Scenario: Insufficient credits
    Given Ana has 1 credit
    And Ana holds tomorrow's hours from 09:00 to 12:00
    When Ana confirms the booking
    Then the booking is refused saying that 2 credits are missing
    And her balance is still 1 credit
    And the hours are free again

  Scenario: Block taken seconds earlier by another student, while choosing
    Given Ana holds tomorrow's hour at 10:00
    When Bruno tries to hold the same hour
    Then Bruno is told that another student is holding it
    And the hour is still Ana's

  @pending
  Scenario: Block taken seconds earlier by another student, while confirming
    Given Ana's hold on tomorrow's hour at 10:00 is about to run out
    And Ana is confirming the booking
    When Bruno takes over the hour and confirms first
    Then Ana is told that the hour was just taken
    And nothing is charged to Ana

  Scenario: Temporary hold expiry
    Given Ana holds tomorrow's hour at 11:00
    When five minutes pass without Ana confirming
    Then the hour is free again
    And Bruno can hold it

  @pending
  Scenario: Failure during the booking
    Given Ana holds tomorrow's hour at 09:00
    When something fails while her booking is being confirmed
    Then her balance is as it was
    And no booking exists
    And the hour is free again

  @pending
  Scenario: The need description travels with the booking and the session
    Given Ana holds tomorrow's hour at 09:00
    When Ana confirms describing "Normal forms before Friday's exam"
    Then the booking carries that description
    And the session of the booking reaches it through the booking

  Scenario: Leaving the confirmation
    Given Ana holds tomorrow's hours from 09:00 to 11:00
    When Ana leaves the confirmation
    Then both hours are free again at once
