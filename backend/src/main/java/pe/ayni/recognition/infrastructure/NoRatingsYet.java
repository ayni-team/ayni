package pe.ayni.recognition.infrastructure;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.recognition.application.SessionRatings;

/**
 * The ratings of the sessions, until reputation can tell them.
 *
 * <p>Reputation keeps the standing of a tutor per skill and has no rating per session to give, so
 * today no session carries a rating into a request. This adapter says so honestly instead of
 * inventing numbers. It is the only thing to replace when reputation publishes them.
 */
@Component
class NoRatingsYet implements SessionRatings {

  @Override
  public Map<UUID, Integer> starsOf(Collection<UUID> sessionIds) {
    return Map.of();
  }
}
