package pe.ayni.reputation.domain.model;

/**
 * There is no completed session with that id in the university: it does not exist, it has not
 * completed, or it belongs to another university, which answers the same.
 */
public class SessionNotRatable extends RuntimeException {

  public SessionNotRatable() {
    super("The session is not available for rating");
  }
}
