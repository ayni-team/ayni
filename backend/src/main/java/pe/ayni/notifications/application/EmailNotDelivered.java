package pe.ayni.notifications.application;

/** The carrier did not take the email. The message says why, and is what the notice records. */
public class EmailNotDelivered extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmailNotDelivered(String message, Throwable cause) {
    super(message, cause);
  }
}
