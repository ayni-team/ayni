package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteCoordinatorRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String fullName) {}