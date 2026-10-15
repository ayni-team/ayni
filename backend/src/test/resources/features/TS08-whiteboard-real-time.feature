# TS08 - The shared whiteboard keeps up in real time.
#
# Each scenario runs with real WebSocket clients against the running backend and a real PostgreSQL,
# with the same name, in WhiteboardRealTimeTest. The times are measured by the clients, from
# sending an operation to the other participant receiving it.
#
# The 300 ms are measured on the 95th percentile of the changes, and no single change may take a
# second or more: a latency target is a percentile, and a shared machine has pauses of its own.

Feature: Real time of the shared whiteboard

  Scenario: The other participant sees a change in less than 300 ms
    Given two participants connected to the board of a session in progress
    When one of them draws
    Then the other receives it in less than 300 ms

  Scenario: A participant who reconnects has the whole board again in less than 3 s
    Given a board with content and a participant who lost the connection
    When they connect again
    Then they have the whole board in less than 3 s

  Scenario: Drawing at the same time ends with both seeing the same board
    Given two participants connected to the same board
    When both draw and erase on the same elements at the same moment
    Then both end with the same board, which is the one stored

  Scenario: Twenty sessions at once keep the threshold
    Given twenty sessions in progress, each with both participants drawing
    When they all draw at the same time
    Then every change reaches the other participant of its session in less than 300 ms
