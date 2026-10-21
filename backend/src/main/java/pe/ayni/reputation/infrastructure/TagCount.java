package pe.ayni.reputation.infrastructure;

import java.util.UUID;

/** How many ratings of a tutor in one skill carried one tag. */
public record TagCount(UUID catalogItemId, String tag, Long times) {}
