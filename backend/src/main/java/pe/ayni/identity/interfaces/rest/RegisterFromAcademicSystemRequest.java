package pe.ayni.identity.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Registering a university from its academic system: the code names its document, and the credits
 * are Ayni's, so the administrator gives them.
 *
 * @param creditValidityDays how long the initial credits last; {@code initialCreditValidityDays},
 *     the name of the manual registration, is read too
 */
@Schema(name = "RegisterFromAcademicSystemRequest")
public record RegisterFromAcademicSystemRequest(
        @NotBlank
        @Schema(example = "USIL")
        String code,

        @Min(1)
        @Schema(example = "5")
        int initialCredits,

        @Min(1)
        @JsonAlias("initialCreditValidityDays")
        @Schema(example = "30")
        int creditValidityDays) {}
