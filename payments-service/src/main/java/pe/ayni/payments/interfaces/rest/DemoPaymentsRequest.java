package pe.ayni.payments.interfaces.rest;

/** The outcomes to switch the simulated payment provider to; at least one is given. */
record DemoPaymentsRequest(String chargeOutcome, String resolutionOutcome) {}
