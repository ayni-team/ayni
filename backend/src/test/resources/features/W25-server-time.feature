# W25 - The web application counts its countdowns against the time of the server. Every answer under
# /api/ says what time the rules are reading, in the header X-Server-Time, as an ISO 8601 instant in
# UTC. The demo profile moves that clock, and a browser's clock may be wrong by itself.
#
# Each scenario names the test that runs it, with the same name, in ServerTimeHeaderTest.

Feature: The time of the server travels in every answer of the API

  Scenario: An answer of the API says the time of the server
    Given the application is running
    When anybody calls a route under /api/, whatever the answer
    Then the answer carries X-Server-Time with the current instant in UTC

  Scenario: The header follows the demo clock when it is moved
    Given the application runs with the demo profile
    When the platform administrator moves the clock two hours ahead
    Then X-Server-Time is two hours ahead of the real time

  Scenario: An answer outside the API does not carry it
    Given the application is running
    When anybody calls a route outside /api/
    Then the answer has no X-Server-Time

  Scenario: A web application on another origin can read it
    Given a browser on an origin the application allows
    When it calls a route of the API
    Then the header is among the ones exposed to that origin
