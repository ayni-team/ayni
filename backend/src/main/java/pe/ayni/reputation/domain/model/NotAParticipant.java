package pe.ayni.reputation.domain.model;

/** The person who tried to rate a session was neither its tutor nor its student. */
public class NotAParticipant extends RuntimeException {

  public NotAParticipant() {
    super("Only participants of the session may submit a rating");
  }
}
