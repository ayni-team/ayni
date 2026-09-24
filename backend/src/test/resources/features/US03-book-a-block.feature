# US03 - As a student, I want to book a block of tutoring with a tutor, so that I get help with a
# subject at an hour that suits us both, paying for it with my credits.
#
# Every scenario names the test that runs it, against a real PostgreSQL. A scenario nobody runs yet
# is tagged @pending and says why.
#
#   Successful booking                   BookABlockAcceptanceTest.successfulBooking
#                                        BookABlockAcceptanceTest.aSecondAttemptIsRefused
#   Credits closest to expiring first    BookABlockAcceptanceTest.creditsClosestToExpiringAreSpentFirst
#   Insufficient credits                 BookABlockAcceptanceTest.insufficientCreditsSayHowManyAreMissing
#                                        BookingConfirmationDatabaseTest.walletsRefusalIsNotAnUnexpectedRollback
#   Block taken, while choosing          BookABlockAcceptanceTest.anHourAnotherStudentHoldsCannotBeTaken
#                                        HoldRaceTest.exactlyOneStudentHoldsTheHour
#   Block taken, while confirming        BookABlockAcceptanceTest.confirmingAnHourAnotherStudentHoldsChargesNothing
#                                        BookingConfirmationDatabaseTest.theVersionColumnDecidesARaceAtConfirmation
#   Temporary hold expiry                BookABlockAcceptanceTest.anUnconfirmedHoldExpiresAfterFiveMinutes
#                                        BookABlockAcceptanceTest.confirmingAnExpiredHoldIsRefused
#                                        ExpiredHoldsJobTest.freesTheExpiredHoldsOfEveryUniversity
#   Failure during the booking           BookABlockAcceptanceTest.aFailureDuringTheBookingLeavesNothingBehind
#                                        BookingConfirmationDatabaseTest.aFailureAfterEverythingWasWrittenLeavesNothingBehind
#   The booking creates the session      BookABlockAcceptanceTest.successfulBooking
#                                        SessionOnBookingConfirmedTest (sessions module)
#   Need description, booking & session  BookABlockAcceptanceTest.theNeedDescriptionTravelsWithTheBooking
#   Leaving the confirmation             BookABlockAcceptanceTest.leavingTheConfirmationGivesTheHoursBack

Feature: Booking a block of tutoring

  Background:
    Given a tutor of the university "UPC", enabled for "Databases I"
    And the tutor is free tomorrow from 09:00 to 12:00, Lima time
    And the students Ana and Bruno

  Scenario: Successful booking
    Given Ana has 5 credits
    And Ana holds tomorrow's hours from 09:00 to 11:00
    When Ana confirms the booking describing what she needs
    Then two credits are deducted from her balance
    And both hours are booked and nobody else can hold them
    And BookingConfirmed is published with both blocks
    And booking the same hours again is refused without charging anything

  Scenario: The booking creates the session
    Given Ana confirmed a booking
    Then a scheduled session exists for that booking, with a room nobody can guess

  @pending
  Scenario: Receiving the access link
    Given Ana confirmed a booking
    Then she receives the link to the session room
    # Not in this story: the link is delivered by notifications, which does not exist yet, and by
    # the participant endpoints of sessions (GET /api/v1/sessions/{id}, POST .../join).

  Scenario: Credits closest to expiring are spent first
    Given Ana has 2 SEED credits expiring in 10 days, 5 ALLOCATED credits expiring in 60 days and 3 EARNED credits
    When Ana books three hours
    Then the 2 SEED credits and 1 ALLOCATED credit are spent
    And her EARNED credits are untouched

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
    And Ana's late confirmation is refused without charging anything

  Scenario: Failure during the booking
    Given Ana holds tomorrow's hour at 09:00
    When something fails while her booking is being confirmed
    Then her balance is as it was
    And no booking exists
    And the hour is free again

  Scenario: The need description travels with the booking
    Given Ana holds tomorrow's hour at 09:00
    When Ana confirms describing "Normal forms before Friday's exam"
    Then the booking carries that description
    And other modules read it through BookingApi

  Scenario: The session of the booking reaches the need description
    Given Ana confirmed a booking describing "Normal forms before Friday's exam"
    Then the session of the booking points at the booking that carries the description

  Scenario: Leaving the confirmation
    Given Ana holds tomorrow's hours from 09:00 to 11:00
    When Ana leaves the confirmation
    Then both hours are free again at once
