# US10 - As a participant of a session, I want a whiteboard both of us can draw on at the same time,
# so that we can work through a problem together.
#
# Each scenario runs over HTTP and real WebSockets against a real PostgreSQL, with the same name, in
# SharedWhiteboardAcceptanceTest. The rules of the board are in WhiteboardTest, without Spring. The
# model and the order of the operations are ADR 0009. The screen of the web application is not part
# of this story's backend and is not built yet.

Feature: Shared whiteboard

  Scenario: Both participants draw at the same time
    Given a session in progress with both participants connected to its whiteboard
    When each of them draws on it at the same time
    Then each sees what the other drew, and their own, on the same board

  Scenario: The content is intact after reconnecting
    Given a participant drew on the board and lost the connection
    When the other kept drawing and the first one connects again
    Then the first one receives the whole board, with everything both drew

  Scenario: The content is kept when the session closes
    Given both participants drew on the board
    When the session closes
    Then the live channels are closed
    And the board is stored as it was left and says there was activity
    And it can still be read and no longer be drawn on

  Scenario: A session without activity is recorded as such
    Given a session in progress where nobody drew
    When the session closes
    Then its board is stored empty and says there was no activity
    And the closing announces that the board had no activity

  # Not scenarios of the story: the contract of the endpoints and of the live channel.
  Scenario: Only the participants of a session in progress open the live channel
    Given a session in progress
    When somebody without a session token, a stranger, somebody of another university, or a participant of a session not in progress tries to open its live channel
    Then they are answered 401, 403, 404 and 409, and nobody is connected

  Scenario: Replacing the whole board needs the version last read
    Given a board somebody changed after the version a participant read
    When that participant replaces it with that version
    Then the answer is 409 and nothing changes
    And with the current version the board is replaced and both participants receive it

  Scenario: An operation the board refuses keeps the channel open
    Given a participant connected to the live channel
    When they send an element with no type, and then a valid one
    Then the first is answered with an error 400 and the second is applied
