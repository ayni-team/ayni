package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "DemoLoginAsRequest")
public record DemoLoginAsRequest(
        @Schema(
                description = "Email of an active student or coordinator, as the personas list shows it",
                example = "u202400001@upc.edu.pe")
        @NotBlank
        @Email
        String email) {}
