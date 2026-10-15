# TS09 - Extracting a module must not force changes in the others, so the move to microservices is a
# migration and not a redesign (ADR 0002).
#
# How each scenario is verified is written in docs/backend-guide.md, "Extracting a module (TS09)".
# Scenario 2 runs in SchemaBoundaryTest with the same name; scenarios 1 and 3 are ModularityTest,
# which CI runs on every pull request; scenario 4 is the build itself.

Feature: Containing the impact of extracting a module

  Scenario: Isolation in the code
    Given a module is about to be extracted
    When the dependencies of the rest of the system on it are reviewed
    Then none points at its internal classes, only at its published interface or its events

  Scenario: No foreign key crosses between the schemas of two modules
    Given every module owns its schema
    When the data model is inspected
    Then no foreign key crosses between the schemas of two modules

  Scenario: Verification on every integration
    Given a change is submitted to continuous integration
    When a module depends on the internal classes of another
    Then the build fails and names the dependency that breaks the rule

  Scenario: The scope of a change is bounded
    Given the internal logic of a module changes without changing its published interface
    When the system is built
    Then no other module needs to change to keep working
