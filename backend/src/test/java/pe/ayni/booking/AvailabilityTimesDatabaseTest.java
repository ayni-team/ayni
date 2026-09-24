package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.SkillsApi;

/**
 * A tutor's wall clock times against a real PostgreSQL, written from a JVM that is not in UTC.
 *
 * <p>A {@code time} column holds a time of day with no zone, so 18:00 has to arrive as 18:00 from
 * any machine. It did not: with {@code hibernate.jdbc.time_zone} set to UTC, Hibernate converted
 * it through {@code java.sql.Time} in the JVM's zone and then wrote it in UTC. From a laptop in
 * Lima, 18:00 to 21:00 reached the table as 23:00 to 02:00 and its CHECK refused the row, while
 * the same request from docker compose, which runs in UTC, went through. Reading shifted the times
 * back in the same JVM, so nothing looked wrong until two machines shared a database.
 *
 * <p>Configured like {@code TutorAvailabilityAcceptanceTest} so both share one Spring context,
 * which is the only reason identity and skills are mocked here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityTimesDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = BookingTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-23T15:00:00Z");
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalTime SIX_PM = LocalTime.of(18, 0);
  private static final LocalTime NINE_PM = LocalTime.of(21, 0);

  private final UUID tutor = UUID.randomUUID();
  private TimeZone zoneOfTheJvm;

  @Autowired private AvailabilityPatternRepository patterns;
  @Autowired private AvailabilityExceptionRepository exceptions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SkillsApi skills;

  /** Five hours behind UTC, like the laptops the team runs the backend on from the IDE. */
  @BeforeEach
  void aJvmInLima() {
    zoneOfTheJvm = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
  }

  @AfterEach
  void restoreTheZoneOfTheJvm() {
    TimeZone.setDefault(zoneOfTheJvm);
  }

  private AvailabilityPattern eveningPattern() {
    return patterns.save(
        new AvailabilityPattern(
            UUID.randomUUID(), UPC, tutor, DayOfWeek.MONDAY, SIX_PM, NINE_PM, MONDAY, null, NOW));
  }

  @Test
  @DisplayName("an evening window is stored at the hours it was declared with")
  void anEveningWindowIsStoredAtTheHoursItWasDeclaredWith() {
    AvailabilityPattern evening = eveningPattern();

    Map<String, Object> row =
        jdbc.queryForMap(
            """
            select starts_at_time::text as starts, ends_at_time::text as ends,
                   valid_from::text as valid_from
            from booking.availability_patterns where id = ?
            """,
            evening.getId());

    assertThat(row)
        .containsEntry("starts", "18:00:00")
        .containsEntry("ends", "21:00:00")
        .containsEntry("valid_from", "2026-10-05");
  }

  @Test
  @DisplayName("an extra evening window on a date is stored at the hours it was declared with")
  void anExtraEveningWindowIsStoredAtTheHoursItWasDeclaredWith() {
    AvailabilityException extra =
        exceptions.save(
            AvailabilityException.addSlot(
                UUID.randomUUID(), UPC, tutor, MONDAY, SIX_PM, NINE_PM, NOW));

    Map<String, Object> row =
        jdbc.queryForMap(
            """
            select starts_at_time::text as starts, ends_at_time::text as ends
            from booking.availability_exceptions where id = ?
            """,
            extra.getId());

    assertThat(row).containsEntry("starts", "18:00:00").containsEntry("ends", "21:00:00");
  }

  @Test
  @DisplayName("a window written by another machine reads back at the hours it holds")
  void aWindowWrittenByAnotherMachineReadsBackAtTheHoursItHolds() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into booking.availability_patterns
          (id, tenant_id, tutor_id, day_of_week, starts_at_time, ends_at_time, valid_from)
        values (?, ?, ?, 1, '18:00', '21:00', '2026-10-05')
        """,
        id,
        UPC,
        tutor);

    AvailabilityPattern read = patterns.findById(id).orElseThrow();

    assertThat(read.getStartsAtTime()).isEqualTo(SIX_PM);
    assertThat(read.getEndsAtTime()).isEqualTo(NINE_PM);
  }

  @Test
  @DisplayName("the moment a window was declared is still stored as that same moment")
  void theMomentAWindowWasDeclaredIsStillStoredAsThatSameMoment() {
    AvailabilityPattern evening = eveningPattern();

    OffsetDateTime createdAt =
        jdbc.queryForObject(
            "select created_at from booking.availability_patterns where id = ?",
            OffsetDateTime.class,
            evening.getId());

    assertThat(createdAt.toInstant()).isEqualTo(NOW);
  }
}
