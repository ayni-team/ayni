# US11 - As a tutor who just finished a session, I want to close it with the student's
# confirmation and see the hours I taught credited, so that I get closer to my certificate with a
# record we both validated.
#
# Scenarios 1 and 3 of the story, and scenario 3 of US54: a session that ends without both presence
# confirmations is unverified. Scenarios 2 and 4 are wallet's, which already grants earned credits
# with no expiry and with the session as their source; here only the earned total is asserted.
# Scenario 5, flagging an abnormally short session, belongs to audit, which does not exist yet.
#
# Every scenario names the test that runs it, against a real PostgreSQL with the real wallet, in
# CloseSessionAcceptanceTest, with the same name. The session's rules are covered without Spring by
# SessionTest, and the refund by WalletEventListenersTest.

Feature: Closing a session

  Background:
    Given Ana booked an hour with Bruno and paid one credit for it
    And both joined the session and received their presence codes

  Scenario: Both confirm the end and the tutor earns the hour
    Given both confirmed their presence
    When Bruno confirms the end
    Then the session waits for Ana, and says when it will close on its own
    When Ana confirms the end
    Then the session is completed
    And Bruno is credited one earned credit, which never expires

  Scenario: Confirming the end again changes nothing
    When Bruno confirms the end twice
    Then the first confirmation is the one recorded, and the session still waits for Ana

  Scenario: With one confirmation, the session closes on its own fifteen minutes after the hour
    Given both confirmed their presence and only Bruno confirmed the end
    When ten minutes have passed since the booked hour ended
    Then the session is still open
    When fifteen minutes have passed
    Then the session is completed, Bruno earns the hour
    And the record shows Bruno confirmed the end and Ana did not

  Scenario: Without both presence confirmations the session is unverified and the student refunded
    Given only Ana confirmed her presence
    When both confirm the end
    Then the session is unverified, naming Bruno as the one who did not confirm
    And Bruno earns nothing and Ana gets her credit back

  Scenario: A session left open by both closes on its own, unverified if nobody proved presence
    Given nobody confirmed presence or the end
    When fifteen minutes have passed since the booked hour ended
    Then the session is unverified, naming both

  Scenario: Both confirming at the same moment close the session once
    Given both confirmed their presence
    When Ana and Bruno confirm the end at the same moment
    Then the session is completed once and Bruno is credited once

  Scenario: The session cannot be ended before both participants check in
    Then a stranger is refused
    And nobody can end a session that has not started
    And nobody can end a session that is already closed
