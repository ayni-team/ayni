package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The confirmation of a retirement.
 *
 * @param confirmedTutors how many tutors the moderator saw as affected, from the usage of the item.
 *     If it is no longer the number of tutors who offer it, nothing is retired
 */
@Schema(name = "RetireCatalogItemRequest", description = "Confirms retiring a catalogue item")
public record RetireCatalogItemRequest(
    @NotNull @Min(0) @Schema(example = "12") Long confirmedTutors) {}
