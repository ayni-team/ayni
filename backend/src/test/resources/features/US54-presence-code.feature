# US54 - As the coordinator who answers for the recognised hours, I need both participants to prove
# they are really in the session, so that the hours add up to tutoring that actually happened.
#
# Codes only go out for a session that started, which since US09 is one both participants entered
# before the ten-minute mark. Scenarios 1, 2 and 4 of the story. Scenario 3, a session that ends without every confirmation
# becoming unverified, is closing the session (US11), and the alert that the audit raises about it
# is "Failed presence verification" in US47-anomalous-usage-alerts.feature.
#
# Every scenario names the test that runs it, against a real PostgreSQL, in
# PresenceCheckAcceptanceTest, with the same name. The code's rules are covered without Spring by
# PresenceCheckTest, and the email by PresenceCodeEmailAcceptanceTest.

Feature: Proving presence with a code sent by email

  Background:
    Given Ana booked an hour with Bruno, which scheduled their session
    And both joined it, which started the session

  Scenario: Five minutes into the session each participant gets a code
    Given the session is ten minutes into its hour
    When the presence codes are looked for
    Then Ana and Bruno each get a six digit code by email, valid for fifteen minutes
    And only its hash is stored
    And looking again sends nothing more

  Scenario: No code before the fifth minute, for a session that never started, or after the end
    Given one session two minutes in, one that never started and one already over
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

  # Resending the code (task US54-T4). Asked by the participant whose email did not arrive, at
  # POST /api/v1/sessions/{id}/presence/resend. They run in PresenceCodeResendAcceptanceTest, with
  # real session tokens and a clock the test moves, and the rule of the limits without Spring in
  # PresenceCheckTest. Every refusal is a 409, the wait between two resends included.

  Scenario: Asking for the code again sends a new one and the old one stops working
    Given the presence codes were sent and Ana's email did not arrive
    When Ana asks for her code again
    Then she is answered that it is on its way, and the answer does not carry the code
    And a new six digit code is sent to her alone, valid for fifteen minutes from now
    And the old code is refused as a wrong code
    And the new code confirms her presence

  Scenario: A code can be sent again three times, at least a minute apart
    When Ana asks again right after a resend
    Then she is refused until a minute has passed since the last one
    When she asks three times a minute apart
    Then the fourth time is refused for good

  Scenario: A code that cannot be used cannot be sent again
    Then a resend is refused when her presence is already confirmed
    And when she already spent her five attempts, which a new code does not give back
    And when no code was sent yet, when she never joined, or when the session is not in progress

  Scenario: Only a participant asks for their own code
    Then a stranger is refused
    And a person of another university finds no such session

  Scenario: Two requests to resend at once send one new code
    When Ana asks for her code again twice at the same moment
    Then one is answered and the other refused, and only one new code is sent

  Scenario: A resent code travels only by email
    When Ana asks for her code again
    Then the notice she receives keeps the session and the expiry but never the code
    And only the hash of the new code is stored
