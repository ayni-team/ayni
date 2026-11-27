# The two doors of the payments service (ADR 0011): the calls the monolith forwards, and the facts
# it owes the monolith. Every scenario has a test with exactly its name: OutboxRelayAcceptanceTest
# against a real PostgreSQL and a fake monolith on a local port, InternalCallsAcceptanceTest over
# HTTP, and OutboxRelayBackoffTest without Spring.

Feature: Calls from the monolith and facts owed to it

  Scenario: A confirmed purchase is delivered to the monolith with the internal token
    Given a purchase was confirmed and its PurchaseConfirmed is in the outbox
    When the relay runs
    Then the monolith receives the event once, with the internal token
    And the message is marked sent and not delivered again

  Scenario: A monolith that does not accept the event is tried again later
    Given the monolith answers 503
    When the relay runs
    Then the message stays unsent with one attempt and a next attempt in the future
    When the monolith recovers and the next attempt is due
    Then the event is delivered

  Scenario: The wait between attempts doubles up to its maximum
    Given an initial wait of 2 seconds and a maximum of 5 minutes
    Then the waits after each failure are 2, 4, 8 and 16 seconds, and never more than 5 minutes

  Scenario: A call without the internal token is refused
    When a request without X-Internal-Token, or with another value, reaches the service
    Then the answer is a 401 and no purchase is read or created

  Scenario: The health check needs no token and reports the database
    When the container's health check asks for /health
    Then the answer is UP
