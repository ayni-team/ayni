# US48 - As a coordinator whose recognition is questioned, I want to consult what happened with
# credits, accreditations and requests, so that I can answer with facts.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# AuditLogAcceptanceTest. The entries are produced by publishing the real events inside a
# transaction, never by inserting rows. The rules of the listeners are in AuditEventListenersTest,
# the ones of the use case in ConsultActivityLogUseCaseTest and the one of the entry in
# ActivityEntryTest. The last scenario is @pending and the pull request lists it under "Still
# pending".

Feature: Audit log

  Scenario: Consulting the log
    Given credits, bookings, sessions and recognition happened in a university
    When a coordinator filters the log by person, by resource or by period
    Then they see the actions with their date, who did them and their result

  Scenario: A log that is not modified
    Given an action was recorded
    When someone tries to change or delete its entry
    Then the database refuses it
    And a correction appears as a new entry

  Scenario: Restricted access
    Given a person who is not an active coordinator of the university
    When they ask for the log
    Then access is refused
    And the attempt is also recorded

  # Not a scenario of the story: the contract of the listeners towards whoever published the event.
  Scenario: A failure to record does not undo what was published
    Given an event whose entry cannot be written
    When it is published
    Then the operation that published it is not affected

  # Not built. Pointing out an entry that does not fit the sequence requires chaining the entries,
  # each one carrying the hash of the previous one, and audit.activity_log in data-model.md has no
  # columns for that. It is a design decision of its own: where the hash lives, how it is computed
  # and who checks it.
  @pending
  Scenario: Detecting tampering
    Given the entries of the log are chained in sequence
    When one of them was altered behind the application
    Then the entry that does not fit the sequence is pointed out
