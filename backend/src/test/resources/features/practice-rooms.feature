Feature: Practice rooms
  As the team of Ayni
  I want to open the screen of a session between two accounts at any time
  So that we can try its video call and its whiteboard without a booking, without credits and
  without leaving any record

  # A tool of the team, not a feature of the product: the room lives only in the memory of the
  # backend and the endpoints of a session answer for it. Off unless
  # ayni.sessions.practice-rooms.enabled is true (AYNI_PRACTICE_ROOMS), on under the demo profile.

  # Every scenario but the last runs with practice rooms enabled.
  Background:
    Given two students of the same university are signed in

  Scenario: Opening a practice room
    When one of them opens a practice room
    Then the answer is 201 with a random session id and the path of its session screen
    And the room reads as the practice room, scheduled, from now until four hours later
    And nothing of it is stored in any table

  Scenario: The first to enter is the tutor and the second the student
    Given a practice room nobody entered
    When one student joins it and then the other
    Then the first is the tutor and the second the student
    And the room is in progress since the second arrived
    And each one reads the name of the other and a pass to the video call of the room

  Scenario: A third person cannot enter a practice room
    Given a practice room both students entered
    When a third student of the same university joins it
    Then the answer is 403
    And reading it, its whiteboard or opening its live channel is refused with 403 too

  Scenario: Somebody who did not enter a full room cannot read it
    Given a practice room both students entered
    When somebody who did not enter reads the room or its whiteboard
    Then the answer is 403, as for a session they do not take part in

  Scenario: What one draws the other receives and the board reads the same
    Given a practice room both students entered
    When both open the live channel of its whiteboard and each one draws
    Then each one receives the operations of both in version order
    And reading the board answers the same elements and version
    And somebody who reconnects receives the whole board again

  Scenario: Ending it by both closes the room
    Given a practice room both students entered and drew on
    When the first one ends it
    Then the room stays in progress waiting for the other
    When the other ends it too
    Then the answer is COMPLETED and nothing is credited
    And the live channels of the board are closed with 4409
    And the room is forgotten: reading it answers 404

  Scenario: An unknown id answers 404 as before
    When a student reads a session id that is neither a session nor an open room
    Then the answer is 404, as it was before practice rooms existed

  Scenario: A whole practice leaves every table as it was
    Given a coordinator of the same university is signed in
    And the rows of every table of every schema are counted
    When one student opens a practice room, both enter, confirm their presence, draw and end it
    Then every table has exactly the rows and the contents it had before
    And no event was published and no event publication was stored
    And the ledger and the audit log still verify as intact

  Scenario: With the switch off opening a practice room answers 404
    Given practice rooms are not enabled
    When a student opens a practice room
    Then the answer is 404 and no room exists
