package pe.ayni.payments.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** The outcomes to switch the simulated payment provider to; at least one is given. */
@Schema(name = "SwitchSimulatedPaymentsRequest")
record DemoPaymentsRequest(
    @Schema(
            description = "What the provider answers to the first charge of a purchase",
            allowableValues = {"CONFIRMED", "REJECTED", "PENDING", "UNAVAILABLE"},
            example = "REJECTED")
        String chargeOutcome,
    @Schema(
            description = "What the provider answers when a pending purchase is checked again",
            allowableValues = {"CONFIRMED", "REJECTED", "PENDING", "UNAVAILABLE"},
            example = "CONFIRMED")
        String resolutionOutcome) {}
