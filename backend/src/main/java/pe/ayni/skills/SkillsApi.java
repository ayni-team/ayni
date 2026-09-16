package pe.ayni.skills;

import java.util.List;
import java.util.UUID;

/**
 * What skills offers to the other modules.
 *
 * <p>Implemented by a class in {@code skills.application}.
 */
public interface SkillsApi {

  /** Whether the tutor is enabled, by grade or by reviewed evidence, to teach the item. */
  boolean isTutorEnabledFor(UUID tutorId, UUID catalogItemId);

  /** Catalogue items the tutor is currently enabled to teach. */
  List<UUID> enabledSkillsOf(UUID tutorId);

  /** @throws java.util.NoSuchElementException when the item is not visible to the current tenant */
  CatalogItemView requireItem(UUID catalogItemId);
}
