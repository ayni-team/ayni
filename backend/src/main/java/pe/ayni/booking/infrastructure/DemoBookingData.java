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
 * Tutors with hours to book, so US03 can be tried in {@code dev}: Bruno at UPC and Mateo at USIL.
 *
 * <p>Each one teaches the courses {@code DemoSkillsData} enabled for him, the ones of his
 * university's curriculum whose grade in his record reaches its threshold, and is free every
 * afternoon from three to six, Lima time. Ana, who has credits, can hold one of Bruno's hours and
 * book it.
 *
 * <p>The window ends at six on purpose. With {@code hibernate.jdbc.time_zone} set to UTC, a
 * backend whose JVM runs on Lima time (from the IDE rather than Docker) stores availability times
 * shifted five hours, and a window that crosses midnight UTC once shifted is refused by {@code
 * ends_at_time > starts_at_time}. Until that is fixed, this window works in both.
 *
 * <p>Everything goes through the real paths: the courses were enabled by academic record, and the
 * availability is declared with the use case the tutor's endpoint calls, which generates the hours
 * for the coming weeks. A tutor who teaches nothing gets no hours: there would be nothing to book.
 *
 * <p>It runs when the application is ready, after every module's {@code ApplicationRunner} has
 * created the universities, the people and the catalogue it depends on. It runs once: a second
 * start finds the windows already there and leaves them alone.
 */
@Component
@Profile("dev")
class DemoBookingData {

  private static final Logger log = LoggerFactory.getLogger(DemoBookingData.class);

  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  /** A tutor of {@code DemoIdentityData} and the university he teaches at. */
  private record Tutor(String tenant, UUID id) {}

  private static final List<Tutor> TUTORS =
      List.of(
          new Tutor("UPC", UUID.fromString("22222222-2222-4222-8222-222222222222")),
          new Tutor("USIL", UUID.fromString("77777777-7777-4777-8777-777777777777")));

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
  void tutorsWithHoursToBook(ApplicationReadyEvent ready) {
    for (Tutor tutor : TUTORS) {
      try {
        TenantContext.runAs(tutor.tenant(), () -> fill(tutor));
      } catch (RuntimeException failure) {
        // Demonstration data must not keep the application from starting: the rest of dev works
        // without a tutor, and the reason is right here in the log.
        log.error("Could not give the demo tutor of {} his hours", tutor.tenant(), failure);
      }
    }
  }

  private void fill(Tutor tutor) {
    if (!patterns.findByTenantIdAndTutorId(tutor.tenant(), tutor.id()).isEmpty()) {
      return;
    }
    List<UUID> teaches = skills.enabledSkillsOf(tutor.id());
    if (teaches.isEmpty()) {
      log.warn("The demo tutor {} of {} teaches nothing: no hours", tutor.id(), tutor.tenant());
      return;
    }

    LocalDate today = LocalDate.now(clock.withZone(LIMA));
    int hours = 0;
    for (DayOfWeek day : DayOfWeek.values()) {
      hours +=
          declareWeeklyAvailability
              .execute(tutor.id(), day, LocalTime.of(15, 0), LocalTime.of(18, 0), today, null)
              .generation()
              .blocksCreated();
    }

    log.info(
        "Demo tutor ready in {}: {} teaches {} courses and has {} bookable hours, every day 15:00-18:00",
        tutor.tenant(),
        tutor.id(),
        teaches.size(),
        hours);
  }
}
