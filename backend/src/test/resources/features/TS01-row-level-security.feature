# TS01 - Isolation between universities enforced by the database: every table with tenant_id has
# row level security, the university is set per transaction, and the application connects with a
# role that cannot bypass it (ADR 0007).
#
# Each scenario runs against a real PostgreSQL, with the same name, in TenantIsolationAcceptanceTest
# (the first one over HTTP with a real session token). The check that no table is left without a
# policy is RowLevelSecurityPolicyTest. Every other integration test of the build also connects as
# the application's role, so the whole suite runs under the policies.

Feature: Row level security per university

  Scenario: A query is scoped to the university of the user
    Given two universities with entries in their activity logs
    When a coordinator of the first one reads the log with their session token
    Then they get the entries of their university and none of the other

  Scenario: A query without an explicit filter is restricted by the policy
    Given two universities with people
    When the application reads every person without filtering by university, bound to the first one
    Then only the people of the first university come back

  Scenario: A request without a university gets nothing
    Given people exist in several universities
    When the application reads them with no university bound
    Then nothing comes back
    And writing a row for a university is refused

  Scenario: An automated check with data of two universities
    Given two universities with rows in several modules
    When every table with tenant_id is read bound to each of them
    Then no table shows a row of the other university, and each sees its own

  # Not scenarios of the story: what TS01 must keep working, and how it is kept honest.
  Scenario: The university does not outlive its transaction
    Given a transaction bound to a university has finished
    When the next transaction on the pool has no university
    Then the database sees no university in it

  Scenario: What crosses universities on purpose visits each one as itself
    Given two universities whose credits expire and that have students
    When the expiry job looks for universities with work, and the administrator lists them
    Then both universities are found, and each one's students are counted

  Scenario: The application's role cannot bypass the policies
    When the role the application connects with is inspected
    Then it is no superuser, has no BYPASSRLS and owns no table
