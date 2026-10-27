# US53 - As the person responsible for the platform at my university, I want to see how much the
# service is used in a period, so that I can support the renewal of the agreement.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# UsageIndicatorsAcceptanceTest, except the last one, in UncoveredDemandAcceptanceTest. The figures
# are produced by the real events, never by inserting rows: published inside a transaction, or, for
# the uncovered demand, caused by real searches. The rules of the period are in IndicatorPeriodTest
# and the one of the day in UniversityDayTest.

Feature: Usage indicators

  Scenario: General indicators
    Given sessions were completed, students activated and recognitions granted in a university
    When a coordinator chooses a period
    Then they see the sessions completed, the hours exchanged, the active students and the recognitions granted
    And the series has one entry per day of the period

  Scenario: Indicators limited to my institution
    Given another university used the service in the same period
    When a coordinator chooses that period
    Then the figures of the other university do not appear

  Scenario: Exporting the indicators
    Given a period with activity
    When a coordinator exports the indicators
    Then they get a file with the figures of the period

  # Not a scenario of the story: what the projection promises towards the modules that publish.
  Scenario: A repeated event is counted once
    Given an event was already counted
    When the same event is delivered again
    Then the figures do not change

  Scenario: Uncovered demand
    Given students searched for courses that no tutor offers
    When a coordinator reads the indicators
    Then they see which courses are sought and not covered
