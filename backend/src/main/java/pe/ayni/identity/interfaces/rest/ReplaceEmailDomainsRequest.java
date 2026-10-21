package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReplaceEmailDomainsRequest(
        @NotEmpty
        List<@NotBlank String> emailDomains) {}
