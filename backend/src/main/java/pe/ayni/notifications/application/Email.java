package pe.ayni.notifications.application;

import java.util.Objects;

/** One plain text email, ready to leave. */
public record Email(String to, String subject, String body) {

  public Email {
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(body, "body must not be null");
  }
}
