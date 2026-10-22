# Scenarios map to ReliableEventsAcceptanceTest, one test per scenario with the same display name,
# except the race of two deliveries, which is wallet's SessionCompletedRaceTest.
# What the registry stores of the three events that carry secrets is also proven without Spring in
# RedactingEventSerializerTest.

Feature: Deliver every event between modules, even when a listener fails or the process stops

  Events cross module boundaries after the publisher commits (ADR 0005). The event publication
  registry stores one publication per event and per listener in the same transaction as the change,
  removes it once the listener completes, and delivers again what is left. Every listener is
  idempotent, so delivering again never repeats an effect.

  Scenario: A publication is stored with the change that produced it, and removed once its listener completes
    Given a session completes and its transaction stores a publication for each listener
    When the transaction commits
    Then the listeners run right away on the same thread, and the tutor is credited
    And no publication is left

  Scenario: A change that rolls back stores no publication
    Given a session completion is published in a transaction that rolls back
    Then no publication is stored and nobody is credited

  Scenario: A listener that fails leaves its publication incomplete, and the retry applies it exactly once
    Given the wallet cannot write when a session completes
    Then the publication to the wallet stays incomplete and the tutor has nothing
    When the wallet can write again and the retry runs more than a minute later
    Then the tutor is credited the hours of the session
    And running the retry again credits nothing more

  Scenario: Two deliveries of the same session completion at once credit the tutor once
    Given the same session completion is delivered twice at the same instant
    When both listeners credit the tutor at once
    Then the tutor is credited once

  Scenario: The retry leaves alone a publication younger than a minute
    Given the wallet could not write a session completion a moment ago
    When the retry runs before a minute has passed
    Then the publication stays incomplete and is not delivered

  Scenario: A publication left incomplete when the process stops is delivered when it starts again
    Given a session completion whose publication to the wallet is incomplete
    When the application stops and starts again
    Then the publication is delivered at startup and the tutor is credited once

  Scenario: No stored publication holds an access link, a presence code or an email address
    Given an access link, a presence code and a student activation are published
    Then each is delivered in full, and the access link and the presence code arrive by email
    And no stored publication contains the link, the code, the student code or an email address

  Scenario: A resumed access link or presence code is dropped, not sent
    Given notifications could not record an access link nor a presence code
    When the retry delivers them again
    Then neither is sent nor recorded, and their publications are complete

  Scenario: A resumed student activation grants the initial credits once
    Given the wallet could not grant the initial credits of an activated student
    When the retry delivers the activation, which no longer carries the email
    Then the student receives the initial credits once

  Scenario: An audit entry is written once when its event arrives twice
    Given a session started
    When the event is delivered twice
    Then the activity log has one entry for it

  Scenario: A no-show notice is written and sent once when its event arrives twice
    Given a session was abandoned by its tutor while the student was there
    When the event is delivered twice
    Then the student has one notice and one email

  Scenario: A purchase confirmation is written and sent once when its event arrives twice
    Given a purchase was confirmed
    When the event is delivered twice
    Then the student has one notice and one email, and the credits once
