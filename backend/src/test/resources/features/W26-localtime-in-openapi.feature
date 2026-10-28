# W26 - The OpenAPI tells the truth about times of day.
#
# java.time.LocalTime travels as "HH:mm:ss" in JSON, but springdoc described it as an object
# {hour, minute, second, nano}, so the types generated for the web (schema.d.ts) were wrong.
# The scenario is run by LocalTimeContractAcceptanceTest, with the same name.

Feature: Times of day in the API description

  Scenario: Times of day are described as strings and travel as strings
    Given a tutor who declared a weekly range from 15:00 to 17:00
    When the API description at /v3/api-docs is read
    Then the start and end of the range are described as a string of format time
    And no schema named LocalTime is left in the description
    And the range read back from the API carries "15:00:00" and "17:00:00"
