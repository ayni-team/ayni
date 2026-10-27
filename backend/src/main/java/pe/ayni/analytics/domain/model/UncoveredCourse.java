package pe.ayni.analytics.domain.model;

import java.util.UUID;

/**
 * A course students searched for in a period and found no hour inside their window, with how many
 * times. The course is an identifier only: whoever shows it resolves the name from the catalogue.
 */
public record UncoveredCourse(UUID catalogItemId, long searches) {}
