# Notices that follow an event - the people concerned are told by email, and the application keeps a
# notice of it for each of them.
#
# These are the notices of several stories, which each of them promises in a line: US03 (the
# confirmed booking, in US03-book-a-block.feature), US05 (a cancellation), US26 (credits about to
# expire), US11 (a session that closed), US29 (a decision on a recognition request) and US16 and
# US43 (a decision on the evidence for a tool). The events are published by the modules of those
# stories; here they are published the way those modules publish them, and notifications reacts.
#
# Every scenario names the test that runs it, against a real PostgreSQL and an SMTP server inside
# the test, in NoticesAcceptanceTest, with the same name. What each email says and what each payload
# keeps is also covered without Spring by DeliverBookingNoticesTest and DeliverOtherNoticesTest, and
# the rules every notice shares (written before it is sent, never twice, a failure recorded) by
# NoticeSenderTest.
#
# The payload of a notice names what happened and never repeats free text: the reason of a decision
# is read where the decision is, and neither the reason nor the address of a person goes to a log.
# The notices are listed by GET /api/v1/notifications (notifications-inbox.feature), which is the
# last scenario.
#
# Not built: the reminder before a session, the preferences of who wants which notice, and the
# notice of reputation at risk.

Feature: Notices that follow an event

  Background:
    Given a university with a student, Ana, and a tutor, Bruno

  # US05
  Scenario: A cancellation by the student tells the tutor
    When Ana cancels a booking less than twelve hours before it starts
    Then Bruno receives an email about it and a BOOKING_CANCELLED notice saying it was late
    And Ana, who cancelled, is told nothing

  # US05
  Scenario: A cancellation by the tutor tells the student
    When Bruno cancels a booking
    Then Ana receives an email saying her credits were returned and a BOOKING_CANCELLED notice
    And Bruno, who cancelled, is told nothing

  # US26
  Scenario: Expiring credits tell the student
    When wallet announces 5 credits that expire in seven days
    Then Ana receives an email with the amount and the day they expire in the university's zone
    And a CREDITS_EXPIRING notice keeps the amount and the instant

  # US11
  Scenario: A completed session tells both and invites the student to rate it
    When a session closes with both participants verified and Bruno earns 2 credits
    Then Ana receives an email inviting her to rate the session
    And Bruno receives an email with the credits he earned
    And both have a SESSION_CLOSED notice with the outcome COMPLETED, Bruno's with the credits

  # US11
  Scenario: An unverified session tells both that nobody earns and the student is refunded
    When a session closes without both presences confirmed
    Then Ana receives an email saying her credits were returned and does not receive an invitation to rate
    And Bruno receives an email saying he earned nothing
    And both have a SESSION_CLOSED notice with the outcome UNVERIFIED

  # US29
  Scenario: A decision on a recognition request tells the student without its reason
    When a coordinator decides Ana's recognition request giving a reason
    Then Ana receives an email and a REQUEST_RESOLVED notice with the request and whether it was approved
    And neither the email nor the notice repeats the reason

  # US16, US43
  Scenario: A decision on the evidence for a tool tells the tutor without its reason
    When a coordinator decides the evidence Bruno presented for a tool giving a reason
    Then Bruno receives an email and a VALIDATION_RESOLVED notice with the request, the tool and whether it was approved
    And neither the email nor the notice repeats the reason

  Scenario: An email the server refuses leaves the notice failed and the event delivered
    Given the mail server is not reachable
    When a booking is confirmed and a session closes
    Then every notice is kept with the reason it did not leave and none is marked sent
    And the events that caused them are delivered all the same

  Scenario: The same event delivered twice tells nobody twice
    When each of the events above is delivered twice
    Then every person has one email and one notice per event

  Scenario: A person who no longer exists does not stop the other notice
    Given Bruno no longer exists in identity
    When a booking is confirmed
    Then Ana is told and nothing is written for Bruno

  Scenario: The notices appear in the inbox of the person they are for
    When a session closes with both participants verified and Bruno earns 2 credits
    Then Ana lists a SESSION_CLOSED notice with her role and Bruno lists his with the credits he earned
