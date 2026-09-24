package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record ConfirmAccessRequest(
        @NotBlank String tenantId,
        @NotBlank String token) {}