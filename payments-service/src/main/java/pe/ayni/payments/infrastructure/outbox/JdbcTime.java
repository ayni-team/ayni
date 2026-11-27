package pe.ayni.payments.infrastructure.outbox;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Instants as PostgreSQL's timestamptz takes them, with no time zone of the JVM involved. */
final class JdbcTime {

  private JdbcTime() {}

  static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
