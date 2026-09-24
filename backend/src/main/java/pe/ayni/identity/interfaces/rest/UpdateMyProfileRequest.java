package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(

        @Size(max = 512)
        String photoUrl,

        @Size(max = 500)
        String bio) {}