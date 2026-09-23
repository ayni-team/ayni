# US19 - As a tutor, I want to publish and adjust my availability, so that students can book the
# subjects I teach without offering hours I cannot honour.
#
# These five scenarios are the acceptance criteria of the story. They cover the complete business
# behaviour of recurring availability, enabled skills, overlapping ranges and confirmed bookings.
# Unit and integration tests should keep the implementation aligned with these scenarios.

Feature: Publishing and adjusting tutor availability

  Background:
    Given a tutor of the university "UPC"

  Scenario: Recurring availability is published
    Given the tutor has the enabled skill "Calculus I"
    And the tutor declares availability on Monday from 09:00 to 12:00
    When the availability is generated for that Monday
    Then three one-hour blocks are created
    And the HoursGenerated event is published for those blocks

  Scenario: Availability is valid for all my enabled skills
    Given the tutor has the enabled skills "Calculus I" and "Physics I"
    And the tutor declares availability on Monday from 09:00 to 10:00
    When the availability is generated for that Monday
    Then the generated hour is available for "Calculus I"
    And the generated hour is available for "Physics I"

  Scenario: Overlapping ranges are rejected
    Given the tutor has weekly availability on Monday from 09:00 to 12:00
    When the tutor declares availability on Monday from 11:00 to 13:00
    Then the new availability is rejected
    And the original availability remains unchanged

  Scenario: Reducing availability with confirmed bookings cancels the affected session
    Given the tutor has a confirmed booking on Monday from 10:00 to 11:00
    When the tutor removes availability on Monday from 10:00 to 11:00
    Then the confirmed booking is cancelled
    And the student is refunded
    And the cancellation is recorded against the tutor

  Scenario: A tutor without enabled skills publishes no availability
    Given the tutor has no enabled skills
    And the tutor declares availability on Monday from 09:00 to 12:00
    When the availability is generated for that Monday
    Then no hour blocks are created
    And the HoursGenerated event is not published
