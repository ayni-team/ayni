package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.Min;

public record UpdateCreditPolicyRequest(

        @Min(1)
        int creditsAmount,

        @Min(1)
        int validityDays) {}