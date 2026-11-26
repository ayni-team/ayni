# The Strangler Fig of ADR 0011: credit purchases moved to the payments service, and the monolith
# keeps their routes, authenticates them as always and forwards them. Every scenario has a test with
# exactly its name: PaymentsGatewayTest against a fake service on a local port,
# PaymentsServiceDownTest with nothing listening at the service's address (QA-06),
# PaymentsServiceUnreachableTest with an address that never connects, and
# DemoPaymentsProxyTest and DemoPaymentsProxyAbsentTest for the switch of the demo profile. What a
# purchase does is the service's: payments-service/src/test/resources/features.

Feature: Credit purchases through the monolith

  Scenario: A purchase is forwarded with the student of the session and answered as the service answers
    Given a student signed in to "UPC"
    When the student buys credits, twice with the same Idempotency-Key, and lists and opens them
    Then the service receives the university, the student, the key and the internal token
    And the student gets the service's 201, then its 200, with its bodies

  Scenario: An error of the payments service reaches the student unchanged
    Given the service refuses a purchase over the monthly limit
    Then the student gets the same 409 and the same body

  Scenario: An invalid purchase is refused before it is forwarded
    When the body or the Idempotency-Key is not valid
    Then the answer is the 400 the payments module gave, and nothing reaches the service

  Scenario: A payments service that does not answer in time is cut off after the timeout
    Given the service takes longer than the timeout to answer
    Then the student gets a 503 that says purchases are unavailable for now, once the timeout is over

  Scenario: A payments service that cannot be connected to is a 503 after the timeout
    Given the address of the service takes the call and never completes the connection
    Then the student gets the same 503 once the timeout is over

  Scenario: A payments service that refuses the monolith's secret does not sign the student out
    Given the service answers 401 to the monolith
    Then the student gets a 503, not a 401 that would read as an expired session

  Scenario: The rest of the platform remains available when the payments service is down
    Given nothing answers at the address of the payments service
    When the student tries to buy credits
    Then the answer is a 503 at once
    And the wallet, the bookings and the search answer as usual

  Scenario: The administrator's switch is forwarded to the payments service
    Given the application runs with the demo profile
    When the platform administrator reads, switches and resets the simulated provider
    Then each call reaches the service with the administrator and the internal token, and no university

  Scenario: An outcome that does not exist is refused with the words of the payments service
    When the administrator switches to an outcome the service does not know
    Then the service's 400 reaches the administrator unchanged

  Scenario: A student cannot switch the payment provider
    When a student asks for the switch
    Then the answer is a 403 and nothing reaches the service

  Scenario: Without the demo profile the payment provider cannot be switched
    Given the application runs without the demo profile
    When the administrator asks for the switch
    Then the answer is a 404
