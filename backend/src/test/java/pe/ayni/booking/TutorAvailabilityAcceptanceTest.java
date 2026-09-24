package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.skills.SkillsApi;

/**
 * US19 over HTTP against a real PostgreSQL: declaring a weekly window is what makes its hours
 * exist. One test per scenario of {@code features/US19-tutor-availability.feature} that booking
 * can answer on its own.
 *
 * <p>identity and skills are the seams: their published interfaces are mocked with the answers the
 * real modules give.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class TutorAvailabilityAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = BookingTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  /** A tutor of their own, so that one scenario cannot see another one's hours. */
  private final UUID tutor = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private HourBlockRepository blocks;
  @Autowired private ApplicationEvents events;
  @Autowired private Clock clock;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SkillsApi skills;

  @BeforeEach
  void aUniversityInLima() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", LIMA.getId(), new BigDecimal("13.00"), true));
  }

  /** Tomorrow's weekday: it occurs exactly four times in the four weeks ahead, all in the future. */
  private String tomorrowsWeekday() {
    return LocalDate.now(clock.withZone(LIMA)).plusDays(1).getDayOfWeek().name();
  }

  private String window(String weekday, String from, String to) {
    return """
        {"dayOfWeek":"%s","startsAtTime":"%s","endsAtTime":"%s","validFrom":"%s"}
        """
        .formatted(weekday, from, to, LocalDate.now(clock.withZone(LIMA)));
  }

  private org.springframework.test.web.servlet.ResultActions declare(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/tutor/availability")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", tutor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private int hoursOfTheTutor() {
    Instant now = clock.instant();
    return blocks.findWithin(UPC, tutor, now.minusSeconds(86_400), now.plusSeconds(60L * 86_400))
        .size();
  }

  private long hoursGeneratedEventsOfTheTutor() {
    return events.stream(HoursGenerated.class).filter(e -> e.tutorId().equals(tutor)).count();
  }

  @Test
  @DisplayName("Recurring availability is published")
  void recurringAvailabilityIsPublished() throws Exception {
    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(UUID.randomUUID()));

    declare(window(tomorrowsWeekday(), "09:00:00", "12:00:00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.generatedHours").value(12))
        .andExpect(jsonPath("$.notice").doesNotExist());

    assertThat(hoursOfTheTutor()).isEqualTo(12);
    assertThat(hoursGeneratedEventsOfTheTutor()).isEqualTo(1);
  }

  @Test
  @DisplayName("Overlapping ranges are rejected")
  void overlappingRangesAreRejected() throws Exception {
    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(UUID.randomUUID()));
    String weekday = tomorrowsWeekday();
    declare(window(weekday, "09:00:00", "12:00:00")).andExpect(status().isCreated());

    declare(window(weekday, "11:00:00", "13:00:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("overlaps")));

    assertThat(hoursOfTheTutor()).isEqualTo(12);
  }

  @Test
  @DisplayName("A tutor without enabled skills publishes no availability")
  void aTutorWithoutEnabledSkillsPublishesNoAvailability() throws Exception {
    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of());

    declare(window(tomorrowsWeekday(), "09:00:00", "12:00:00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.generatedHours").value(0))
        .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("no enabled skill")));

    assertThat(hoursOfTheTutor()).isZero();
    assertThat(hoursGeneratedEventsOfTheTutor()).isZero();
  }
}
