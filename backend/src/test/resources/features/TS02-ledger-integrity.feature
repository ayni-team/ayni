# TS02 - The credit ledger cannot be changed once written, so that the balance of any student can be
# rebuilt and a recognition request has a verifiable record behind it.
#
# How each scenario is verified:
#   Immutable movements          WalletLedgerTest (the append only trigger refuses UPDATE and DELETE)
#   Correction by compensation   WalletLedgerTest (a refund is a new entry; the charge is untouched)
#   Rebuildable balance          WalletLedgerTest (credits minus debits equals the balance)
#   Detection of tampering       LedgerIntegrityAcceptanceTest, over HTTP against a real PostgreSQL:
#                                GET /api/v1/coordinator/ledger/integrity
#
# The test of the last scenario damages a row the way only the owner of the database could, with the
# trigger switched off for that one statement and on again right after. LedgerChainTest proves the
# rule without a database.

Feature: Integrity of the credit ledger

  Scenario: Immutable movements
    Given a credit movement was recorded
    When someone tries to change or delete it
    Then the operation is rejected and the original record stays intact

  Scenario: Correction by compensation
    Given an operation has to be corrected
    When the correction is applied
    Then a new movement is recorded that compensates it and no earlier one is changed

  Scenario: Rebuildable balance
    Given the complete history of movements of a student
    When they are added up in order
    Then the result matches exactly the balance the platform shows

  Scenario: Detection of tampering
    Given an entry of the ledger was altered outside the application
    When the integrity of the chain is verified
    Then the system identifies the inconsistent entry and reports it
