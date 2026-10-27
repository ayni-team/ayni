package pe.ayni.reputation;

import java.util.Optional;
import java.util.UUID;

/**
 * What reputation offers to the other modules.
 *
 * <p>Implemented by a class in {@code reputation.application}.
 */
public interface ReputationApi {

  /** The tutor's standing in one skill, or empty if they never taught it. */
  Optional<TutorStandingView> standingOf(UUID tutorId, UUID catalogItemId);
}
