# US06 - As a student who waited for a session the tutor never attended, I want my credits
# returned automatically, so I do not lose balance because of someone else's absence.
#
# A tutor no-show is detected ten minutes after the scheduled start. The session is abandoned and
# the booking is marked NO_SHOW. The student is refunded, notified, and the tutor's history updated
# only when the student confirmed their presence. When neither participant checked in, there is no
# refund and no tutor no-show is recorded.

Feature: Automatic recovery of credits after a tutor no-show

  Background:
    Given Ana booked an hour with Bruno and paid one credit for it

  Scenario: The tutor does not check in and the attending student is refunded automatically
    Given Ana checked in to the session
    And Bruno did not check in
    When ten minutes have passed since the scheduled start
    Then the session is abandoned
    And the booking is marked NO_SHOW
    And Ana's credit is returned automatically
    And Ana is notified that her credit was returned
    And Bruno's no-show is recorded in his compliance history

  Scenario: A tutor no-show is included in the tutor's history after recalculation
    Given Ana checked in to the session
    And Bruno did not check in
    When the system processes the tutor no-show
    Then Bruno's compliance history contains the session

  Scenario: Neither participant checks in, so there is no refund or tutor no-show
    Given Ana did not check in to the session
    And Bruno did not check in
    When ten minutes have passed since the scheduled start
    Then the session is abandoned
    And the booking is marked NO_SHOW
    And Ana's credit is not returned
    And no tutor no-show is recorded in Bruno's compliance history
