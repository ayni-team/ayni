# US04 - As a student with several tutorings booked in the week, I want to see them together in one
# place with their time and their access, so that I reach each one without searching my email.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# UpcomingTutoringsAcceptanceTest, signed in with real session tokens (TS03). The bookings are made
# the way a student makes them, by holding and booking a tutor's hours over HTTP. The rules of which
# half a booking is in and when it can be entered are covered without Spring by
# BookingTimelineTest. GET /api/v1/bookings/mine answers both halves, one page at a time.

Feature: The student's tutorings

  Background:
    Given a tutor of my university free tomorrow from nine to twelve, Lima time

  Scenario: Sorted list
    Given I have confirmed bookings
    When I open my tutorings
    Then I see them from the nearest to the farthest with the course, the tutor, the date, the time and the status
    And they come one page at a time

  Scenario: A tutoring about to start
    Given one of my tutorings starts in less than fifteen minutes
    When I see the list
    Then that one is highlighted as one I can enter now

  Scenario: Consulting the history
    Given some of my tutorings already took place or were cancelled
    When I open my tutorings
    Then I can consult the history apart from the upcoming ones

  # Not a scenario of the story: whose bookings a student reads.
  Scenario: Only my own tutorings of my university
    Given another student and another university have bookings too
    When I open my tutorings
    Then I see only mine

  # Not built. The highlighted tutoring should open its session, and joining needs the session's
  # identifier, which sessions creates when it hears BookingConfirmed. Booking cannot ask sessions
  # for it, because sessions already depends on booking and the modules would depend on each
  # other. It needs a decision of the lead: sessions answering the session of a booking, or the
  # list being assembled by the client from both.
  @pending
  Scenario: The highlighted tutoring opens its session
    Given one of my tutorings starts in less than fifteen minutes
    When I choose it in the list
    Then I enter its session
