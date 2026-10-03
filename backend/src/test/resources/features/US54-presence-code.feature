# US54 - As the coordinator who answers for the recognised hours, I need both participants to prove
# they are really in the session, so that the hours add up to tutoring that actually happened.
#
# Scenarios 1, 2 and 4 of the story. Scenario 3, a session that ends without every confirmation
# becoming unverified, arrives with closing the session (US11).
#
# Every scenario names the test that runs it, against a real PostgreSQL, in
# PresenceCheckAcceptanceTest, with the same name. The code's rules are covered without Spring by
# PresenceCheckTest, and the email by PresenceCodeEmailAcceptanceTest.

Feature: Proving presence with a code sent by email

  Background:
    Given Ana booked an hour with Bruno, which scheduled their session
    And both joined it

  Scenario: Five minutes into the session each participant gets a code
    Given the session is ten minutes into its hour
    When the presence codes are looked for
    Then Ana and Bruno each get a six digit code by email, valid for fifteen minutes
    And only its hash is stored
    And looking again sends nothing more

  Scenario: No code before the fifth minute, for a session nobody joined, or after the end
    Given one session two minutes in, one nobody joined and one already over
    When the presence codes are looked for
    Then none of them gets a code

  Scenario: Typing the code confirms presence, and typing it again changes nothing
    When Ana types her code
    Then her presence is confirmed and Bruno's is not
    When she types it again
    Then nothing changes

  Scenario: A participant cannot confirm with the other participant's code
    When Bruno types Ana's code
    Then it is refused as a wrong code

  Scenario: A wrong code spends one attempt, and the attempt is kept
    When Ana types a wrong code
    Then she is told four attempts are left, and the attempt is recorded
    And her right code still works

  Scenario: After five wrong codes the code can no longer be used
    When Ana types a wrong code five times
    Then her code is useless, even the right one

  Scenario: Wrong codes sent at once still stop at five
    When eight wrong codes for Ana arrive at the same moment
    Then five are counted and the other three are refused without counting

  Scenario: An expired code is refused
    Given Ana's code expired
    When she types it
    Then it is refused, and no attempt is spent

  Scenario: Presence cannot be confirmed before the code, before the session starts, or by a stranger
    Then a code typed before it is sent is refused
    And a participant cannot confirm while the other participant has not joined
    And a stranger is refused
    And a code that is not six digits is refused without spending an attempt
