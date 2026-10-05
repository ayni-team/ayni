package pe.ayni.recognition.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The stars the tutor received for each session, where there are any.
 *
 * <p>A port, because the ratings belong to reputation and that module does not publish them per
 * session yet. Recognition asks here and does not care where the answer comes from: when reputation
 * offers it, an adapter replaces the one that has nothing to say, and nothing else changes.
 */
public interface SessionRatings {

  /**
   * @return the stars, 1 to 5, of the sessions that have been rated; the others are not in the map
   */
  Map<UUID, Integer> starsOf(Collection<UUID> sessionIds);
}
