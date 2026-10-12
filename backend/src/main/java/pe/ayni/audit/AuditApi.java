package pe.ayni.audit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What audit offers to the other modules.
 *
 * <p>Implemented by a class in {@code audit.application}.
 */
public interface AuditApi {

  /**
   * The alerts still open about each of the given sessions, in the current university.
   *
   * <p>Only a session that has at least one is in the answer. A dismissed alert is not open and does
   * not appear. Used by recognition to put the alerts next to the sessions a coordinator reviews.
   */
  Map<UUID, List<AlertView>> openAlertsOf(Collection<UUID> sessionIds);
}
