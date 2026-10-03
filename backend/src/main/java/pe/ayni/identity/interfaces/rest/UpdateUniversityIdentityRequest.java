package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.Size;

public record UpdateUniversityIdentityRequest(

        @Size(max = 512)
        String logoUrl,

        @Size(max = 16)
        String primaryColor,

        @Size(max = 16)
        String secondaryColor) {}