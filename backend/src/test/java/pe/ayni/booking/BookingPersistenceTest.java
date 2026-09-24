package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.infrastructure.BookingRepository;

/**
 * {@code booking.bookings} against the PostgreSQL that has to keep its promises: the entity maps
 * to the table Flyway created, the repository only answers within a university, and the
 * constraints refuse what the entity would never build.
 */
@SpringBootTest
class BookingPersistenceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = BookingTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
  private final Instant startsAt = now.plus(Duration.ofDays(1));

  @Autowired private BookingRepository bookings;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("a booking is read back only within its own university")
  void readsBackWithinItsUniversity() {

    Booking saved =
        bookings.save(
            Booking.confirm(
                UUID.randomUUID(),
                UPC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                startsAt,
                startsAt.plus(Duration.ofHours(2)),
                2,
                "Joins and subqueries",
                now));

    Booking found = bookings.findByTenantIdAndId(UPC, saved.getId()).orElseThrow();
    assertThat(found.getHours()).isEqualTo(2);
    assertThat(found.getCreditsCharged()).isEqualTo(2);
    assertThat(found.getNeedDescription()).isEqualTo("Joins and subqueries");
    assertThat(found.getStartsAt()).isEqualTo(startsAt);

    assertThat(bookings.findByTenantIdAndId("PUCP", saved.getId())).isEmpty();
  }

  @Test
  @DisplayName("the database refuses a price that is not one credit per hour")
  void refusesAPriceThatIsNotOneCreditPerHour() {
    assertThatThrownBy(() -> insert(2, 3, "CONFIRMED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_bookings_pricing");
  }

  @Test
  @DisplayName("the database refuses a booking of no hours")
  void refusesABookingOfNoHours() {
    assertThatThrownBy(() -> insert(0, 0, "CONFIRMED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_bookings_pricing");
  }

  @Test
  @DisplayName("the database refuses a cancellation that does not say who cancelled and when")
  void refusesAnAnonymousCancellation() {
    assertThatThrownBy(() -> insert(1, 1, "CANCELLED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_bookings_cancellation");
  }

  @Test
  @DisplayName("the database refuses a status the model does not know")
  void refusesAnUnknownStatus() {
    assertThatThrownBy(() -> insert(1, 1, "PENDING", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_bookings_status");
  }

  private void insert(int hours, int credits, String status, String cancelledBy) {
    jdbc.update(
        """
        insert into booking.bookings
          (id, tenant_id, student_id, tutor_id, catalog_item_id, starts_at, ends_at, hours,
           credits_charged, need_description, status, cancelled_by)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        UPC,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Timestamp.from(startsAt),
        Timestamp.from(startsAt.plus(Duration.ofHours(Math.max(hours, 1)))),
        hours,
        credits,
        "Anything",
        status,
        cancelledBy);
  }
}
