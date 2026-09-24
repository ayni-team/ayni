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

  /**
   * Registers catalogue items a student needs help with, for US40's initial configuration.
   * Declaring the same item twice is harmless.
   */
  void declareLearningInterests(UUID studentId, List<UUID> catalogItemIds);

  /**
   * Enables, by academic record, every item among these the student's approved courses already
   * clear, for the "can teach" half of US40's initial configuration.
   *
   * <p>An item already offered, not backed by an approved course, or not a university course at
   * all is skipped rather than refused, so a caller resuming an interrupted configuration does not
   * need to filter the list first.
   *
   * @return the catalogue items that ended up enabled
   */
  List<UUID> declareTeachingInterests(UUID studentId, List<UUID> catalogItemIds);
}
