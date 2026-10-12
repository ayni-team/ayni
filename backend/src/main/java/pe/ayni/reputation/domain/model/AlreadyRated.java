package pe.ayni.reputation.domain.model;

/** That participant already rated the session in their direction: one rating per session and direction. */
public class AlreadyRated extends RuntimeException {

  public AlreadyRated() {
    super("This participant already rated the session");
  }
}
