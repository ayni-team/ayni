package pe.ayni.shared.tenancy;

/** Raised when an operation that requires a person runs without one bound to the request. */
public class MissingUserException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MissingUserException() {
    super("No user is bound to the current request. Send the X-User-Id header.");
  }
}
