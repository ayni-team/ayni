package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The item that stays when two are joined. The one in the path is the duplicate and is retired.
 *
 * @param intoCatalogItemId the item that keeps everything the duplicate had
 */
@Schema(name = "MergeCatalogItemsRequest", description = "Joins a duplicate item to the one that stays")
public record MergeCatalogItemsRequest(
    @NotNull @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID intoCatalogItemId) {}
