# Demo payments switch - under the demo profile the platform administrator changes what the
# simulated payment provider answers, and reads or closes the circuit in front of it, while the
# application runs, so a presentation can show a rejected purchase, a pending one that is resolved
# later and an outage of the provider (TS06) without restarting.
#
# This is not a story of the backlog, so it carries no US number: it belongs to the demo profile
# (docs/demo.md). With the default properties nothing about payments changes.
#
# Every scenario names the test that runs it, with the same name: the outcomes without Spring in
# SimulatedPaymentSettingsTest; the endpoints against a real PostgreSQL, the real provider and real
# session tokens in DemoPaymentsSwitchTest; the application without the profile in
# DemoPaymentsSwitchAbsentTest.

Feature: Switching the simulated payment provider at runtime

  Background:
    Given the application runs with the demo profile
    And the platform administrator signed in

  Scenario: Switching the charge outcome changes the next purchase without a restart
    Given the provider confirms
    When the administrator switches the charge outcome to REJECTED, PENDING and CONFIRMED in turn
    Then the next purchase is failed, pending and confirmed in that order

  Scenario: A pending purchase is confirmed after the resolution outcome is switched
    Given a purchase the provider left pending
    When the administrator switches the resolution outcome to CONFIRMED and the provider is checked
    Then the purchase is confirmed and the student has the credit

  Scenario: Three unavailable answers open the circuit and the administrator reads it
    Given the administrator switched the charge outcome to UNAVAILABLE
    When three purchases are attempted
    Then each stays pending and the administrator reads an open circuit with three failures

  Scenario: Resetting the circuit lets the next purchase ask the provider again
    Given the circuit is open
    When the administrator resets it
    Then it reads closed and the next purchase is confirmed

  Scenario: An outcome that does not exist is refused and nothing changes
    When the administrator switches to an outcome that is not CONFIRMED, REJECTED, PENDING or UNAVAILABLE
    Then the answer is a 400 and the outcomes stay as they were

  Scenario: A student cannot switch the payment provider
    When a student asks for the switch
    Then the answer is a 403

  Scenario: Without the demo profile the payment provider cannot be switched
    Given the application runs without the demo profile
    When the administrator asks for the switch
    Then the answer is a 404

  Scenario: A configured outcome that is not allowed stops the start with a clear message
    Given ayni.payments.simulated-outcome is MAYBE
    When the application starts
    Then it fails saying which values are allowed

  Scenario: The settings start from the configured outcomes
    Given the two properties name two outcomes
    Then the provider answers them until they are switched

  Scenario: Switching changes only the outcomes that are given
    When the administrator switches only one of the two outcomes
    Then the other stays as it was
