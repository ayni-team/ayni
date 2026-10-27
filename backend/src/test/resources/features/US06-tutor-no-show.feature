# US06 - As a student who waited for a session the tutor never attended, I want my credits
# returned automatically, so I do not lose balance because of someone else's absence.
#
# An absence is detected ten minutes after the scheduled start (US09: a session starts only when both
# participants checked in before then). The session is abandoned and the booking is marked NO_SHOW.
# Who pays depends on who was missing:
#
#   tutor missing, student present   the student is refunded and notified, and the absence is
#                                    recorded in the tutor's reliability history
#                                    (booking.tutor_reliability, kind NO_SHOW)
#   student missing, tutor present   no refund, no notice, nothing against the tutor, and the tutor
#                                    earns no credits: the hour was lost by the one who did not come
#   both missing                     no refund and nothing recorded against the tutor
#
# Each scenario is run by a test of pe.ayni.sessions.TutorNoShowAcceptanceTest with its title as
# display name.

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
    And Bruno's no-show is recorded in his reliability history

  Scenario: The tutor's no-show is recorded in their reliability history
    Given Ana checked in to the session
    And Bruno did not check in
    When the system processes the tutor no-show
    Then Bruno's reliability history contains a no-show for the booking

  Scenario: Neither participant checks in, so there is no refund or tutor no-show
    Given Ana did not check in to the session
    And Bruno did not check in
    When ten minutes have passed since the scheduled start
    Then the session is abandoned
    And the booking is marked NO_SHOW
    And Ana's credit is not returned
    And no tutor no-show is recorded in Bruno's reliability history

  Scenario: The student does not check in and the attending tutor is neither penalized nor paid
    Given Bruno checked in to the session
    And Ana did not check in
    When ten minutes have passed since the scheduled start
    Then the session is abandoned
    And the booking is marked NO_SHOW
    And Ana's credit is not returned
    And Ana is not sent a tutor no-show notice
    And no tutor no-show is recorded in Bruno's reliability history
    And Bruno earns no credits
