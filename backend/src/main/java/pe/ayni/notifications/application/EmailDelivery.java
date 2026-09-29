package pe.ayni.notifications.application;

/**
 * Hands an email to whatever carries it.
 *
 * <p>A port, so the rules about what a notice says and how its outcome is recorded are tested
 * without a mail server, and so the carrier can change without touching them. Implemented over SMTP
 * in {@code notifications.infrastructure}.
 */
public interface EmailDelivery {

  /** @throws EmailNotDelivered when the carrier refused the email or could not be reached */
  void send(Email email);
}
