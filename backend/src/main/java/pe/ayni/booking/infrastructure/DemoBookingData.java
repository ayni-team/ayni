package pe.ayni.booking.infrastructure;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pe.ayni.booking.application.DeclareWeeklyAvailabilityUseCase;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;

/**
 * A tutor with hours to book, so US03 can be tried in {@code dev}.
 *
 * <p>Bruno, the second student of the other modules' demo data, teaches two courses his grades
 * already justify, and is free every afternoon from three to six, Lima time. Ana, who has credits,
 * can hold one of those hours and book it.
 *
 * <p>The window ends at six on purpose. With {@code hibernate.jdbc.time_zone} set to UTC, a
 * backend whose JVM runs on Lima time (from the IDE rather than Docker) stores availability times
 * shifted five hours, and a window that crosses midnight UTC once shifted is refused by {@code
 * ends_at_time > starts_at_time}. Until that is fixed, this window works in both.
 *
 * <p>Everything goes through the real paths: the courses are enabled by academic record through
 * {@link SkillsApi}, and the availability is declared with the use case the tutor's endpoint calls,
 * which generates the hours for the coming weeks. Demonstration data that took a shortcut the
 * application cannot take would prove nothing.
 *
 * <p>It runs when the application is ready, after every module's {@code ApplicationRunner} has
 * created the university, the students and the catalogue it depends on. It runs once: a second
 * start finds Bruno's skills and windows already there and leaves them alone.
 */
@Component
@Profile("dev")
class DemoBookingData {

  private static final Logger log = LoggerFactory.getLogger(DemoBookingData.class);

  private static final String TENANT = "UPC";
  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  /** The same student as in {@code DemoWalletData} and {@code DemoIdentityData}. */
  private static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");

  /** Software Architecture Fundamentals and Databases I, from {@code DemoSkillsData}. */
  private static final List<UUID> BRUNOS_COURSES =
      List.of(
          UUID.fromString("b0000000-0000-4000-8000-000000000101"),
          UUID.fromString("b0000000-0000-4000-8000-000000000102"));

  private final SkillsApi skills;
  private final DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability;
  private final AvailabilityPatternRepository patterns;
  private final Clock clock;

  DemoBookingData(
      SkillsApi skills,
      DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability,
      AvailabilityPatternRepository patterns,
      Clock clock) {
    this.skills = skills;
    this.declareWeeklyAvailability = declareWeeklyAvailability;
    this.patterns = patterns;
    this.clock = clock;
  }

  @EventListener
  void aTutorWithHoursToBook(ApplicationReadyEvent ready) {
    try {
      TenantContext.runAs(TENANT, this::fill);
    } catch (RuntimeException failure) {
      // Demonstration data must not keep the application from starting: the rest of dev works
      // without a tutor, and the reason is right here in the log.
      log.error("Could not create the demo tutor of {}", TENANT, failure);
    }
  }

  private void fill() {
    if (skills.enabledSkillsOf(BRUNO).isEmpty()) {
      skills.declareTeachingInterests(BRUNO, BRUNOS_COURSES);
    }
    if (!patterns.findByTenantIdAndTutorId(TENANT, BRUNO).isEmpty()) {
      return;
    }

    LocalDate today = LocalDate.now(clock.withZone(LIMA));
    int hours = 0;
    for (DayOfWeek day : DayOfWeek.values()) {
      hours +=
          declareWeeklyAvailability
              .execute(BRUNO, day, LocalTime.of(15, 0), LocalTime.of(18, 0), today, null)
              .generation()
              .blocksCreated();
    }

    log.info(
        "Demo tutor ready in {}: {} teaches {} and has {} bookable hours, every day 15:00-18:00",
        TENANT,
        BRUNO,
        skills.enabledSkillsOf(BRUNO),
        hours);
  }
}
