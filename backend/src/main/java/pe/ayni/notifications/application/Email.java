package pe.ayni.notifications.application;

import java.util.Objects;

/**
 * One email, ready to leave.
 *
 * @param body the plain text, which every email has and which is all a client without HTML shows
 * @param html the same message as HTML, or {@code null} for an email that is only plain text
 */
public record Email(String to, String subject, String body, String html) {

  public Email {
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(body, "body must not be null");
  }

  /** An email that is only plain text. */
  public Email(String to, String subject, String body) {
    this(to, subject, body, null);
  }
}
