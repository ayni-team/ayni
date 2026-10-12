# purchase listing and receipt -> aStudentCanViewPurchaseDetailsAndReceipt
# purchase isolation -> aStudentCannotViewAnotherStudentsPurchaseDetails

Feature: Check credit purchase details

  Scenario: The student lists purchases and gets a confirmed purchase receipt
    Given the student has made a credit purchase
    When the student checks their purchases
    Then each purchase shows its date, credits, amount and status
    When the student opens the confirmed purchase
    Then the receipt shows the purchase details and provider confirmation
    And the receipt points to the matching wallet movement

  Scenario: A student cannot read another student's purchase
    Given another student has made a credit purchase
    When the student opens that purchase
    Then the purchase is not found
