package pe.ayni.skills;

import java.util.UUID;

/**
 * A catalogue item as the other modules see it.
 *
 * @param courseCode {@code null} for global items
 */
public record CatalogItemView(UUID id, CatalogScope scope, String name, String courseCode) {}
