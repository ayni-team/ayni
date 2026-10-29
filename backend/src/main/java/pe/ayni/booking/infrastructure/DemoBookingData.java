package pe.ayni.booking.infrastructure;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.application.AddAvailabilityExceptionUseCase;
import pe.ayni.booking.application.AddAvailabilityPauseUseCase;
import pe.ayni.booking.application.DeclareWeeklyAvailabilityUseCase;
import pe.ayni.booking.domain.model.ExceptionKind;
import pe.ayni.shared.demo.DemoBookings.BookedTutoring;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.demo.DemoCast.Person;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;

/**
 * Tutors with hours to book, and tutorings already booked, so the search, a student's tutorings
 * and a tutor's agenda have something to show in {@code dev}.
 *
 * <p>Four tutors in each university, with different weeks, all in Lima time:
 *
 * <ul>
 *   <li>Bruno at UPC and Mateo Ríos at USIL, every afternoon from three to six, as they always
 *       were;
 *   <li>Mateo Cárdenas and Joaquín Medina, weekday mornings from eight to eleven, with a weekday off
 *       a week from now (a date exception);
 *   <li>Daniela Salas and Daniela Tapia, weekends from nine to one, with a week of pause two weeks
 *       from now;
 *   <li>Isabella Tapia and Piero Delgado, one hour on Tuesdays and Thursdays, from five to six.
 * </ul>
 *
 * <p>Each one teaches the courses {@code DemoSkillsData} enabled for them. Then, in each
 * university, four tutorings between two and seven days ahead, booked by students of {@code
 * DemoCast} as a student books (held, confirmed, charged), and one more booked and cancelled by its
 * student, with the refund it gives back.
 *
 * <p>The windows end at six at the latest on purpose. With {@code hibernate.jdbc.time_zone} set to
 * UTC, a backend whose JVM runs on Lima time (from the IDE rather than Docker) stores availability
 * times shifted five hours, and a window that crosses midnight UTC once shifted is refused by {@code
 * ends_at_time > starts_at_time}. Until that is fixed, these windows work in both.
 *
 * <p>The windows are valid from eight weeks ago, so the history of the {@code demo} profile finds
 * hours in the weeks it plays; nothing generates hours before the day the clock reads.
 *
 * <p>Everything goes through the real paths: the availability, the exception and the pause are
 * declared with the use cases the tutor's endpoints call, and the tutorings with those of the
 * student's ({@link DemoTutorings}). A tutor who teaches nothing gets no hours.
 *
 * <p>It runs when the application is ready, after every module's {@code ApplicationRunner} has
 * created the universities, the people, their credits and the catalogue it depends on. A tutor's
 * week is declared once: a second start finds the windows already there and leaves them alone. The
 * upcoming tutorings are booked again whenever a university has none left, so a database seeded
 * weeks ago does not show only the past; the cancelled one, only if the university has none.
 */
@Component
@Profile("dev")
class DemoBookingData {

  private static final Logger log = LoggerFactory.getLogger(DemoBookingData.class);

  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  private static final Set<DayOfWeek> WEEKDAYS =
      Set.of(
          DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY);

  /** The week of a tutor: on which days, from when to when. */
  private enum Week {
    EVERY_AFTERNOON(Set.of(DayOfWeek.values()), 15, 18),
    WEEKDAY_MORNINGS(WEEKDAYS, 8, 11),
    WEEKENDS(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 9, 13),
    TWO_HOURS_A_WEEK(Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 17, 18);

    private final Set<DayOfWeek> days;
    private final LocalTime from;
    private final LocalTime to;

    Week(Set<DayOfWeek> days, int from, int to) {
      this.days = days;
      this.from = LocalTime.of(from, 0);
      this.to = LocalTime.of(to, 0);
    }
  }

  /** A tutor of {@code DemoCast} and their week. */
  private record Tutor(Person person, Week week) {}

  private static final List<Tutor> TUTORS =
      List.of(
          new Tutor(DemoCast.BRUNO, Week.EVERY_AFTERNOON),
          new Tutor(DemoCast.MATEO_CARDENAS, Week.WEEKDAY_MORNINGS),
          new Tutor(DemoCast.DANIELA_SALAS, Week.WEEKENDS),
          new Tutor(DemoCast.ISABELLA_TAPIA, Week.TWO_HOURS_A_WEEK),
          new Tutor(DemoCast.MATEO, Week.EVERY_AFTERNOON),
          new Tutor(DemoCast.JOAQUIN_MEDINA, Week.WEEKDAY_MORNINGS),
          new Tutor(DemoCast.DANIELA_TAPIA, Week.WEEKENDS),
          new Tutor(DemoCast.PIERO_DELGADO, Week.TWO_HOURS_A_WEEK));

  /** One tutoring ahead: who books whom, from how many days ahead, and what for. */
  private record Upcoming(Person student, Person tutor, int fromDays, String need) {}

  private static final List<Upcoming> UPCOMING =
      List.of(
          new Upcoming(DemoCast.ANA, DemoCast.BRUNO, 2,
              "Repasar normalización antes del examen parcial."),
          new Upcoming(DemoCast.MILAGROS_CASTILLO, DemoCast.MATEO_CARDENAS, 3,
              "No entiendo bien la herencia y el polimorfismo."),
          new Upcoming(DemoCast.ISABELLA_TAPIA, DemoCast.DANIELA_SALAS, 2,
              "Ejercicios de árboles binarios para la práctica calificada."),
          new Upcoming(DemoCast.MILAGROS_CASTILLO, DemoCast.ISABELLA_TAPIA, 2,
              "Ordenar las ideas del trabajo final antes de entregarlo."),
          new Upcoming(DemoCast.ROCIO_PALOMINO, DemoCast.MATEO, 2,
              "Dudas sobre el modelo entidad relación del proyecto."),
          new Upcoming(DemoCast.ROCIO_PALOMINO, DemoCast.JOAQUIN_MEDINA, 3,
              "Practicar recursión con ejercicios de examen."),
          new Upcoming(DemoCast.PIERO_DELGADO, DemoCast.DANIELA_TAPIA, 2,
              "Intervalos de confianza y pruebas de hipótesis."),
          new Upcoming(DemoCast.ROCIO_PALOMINO, DemoCast.PIERO_DELGADO, 2,
              "Revisar el diseño de la experiencia de usuario de mi proyecto."));

  /** The tutoring each university has cancelled by its student, a few days ahead. */
  private static final List<Upcoming> CANCELLED =
      List.of(
          new Upcoming(DemoCast.MILAGROS_CASTILLO, DemoCast.BRUNO, 4,
              "Consultas de SQL (al final no pude ir)."),
          new Upcoming(DemoCast.ROCIO_PALOMINO, DemoCast.MATEO, 5,
              "Consultas de SQL (al final no pude ir)."));

  private final SkillsApi skills;
  private final DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability;
  private final AddAvailabilityExceptionUseCase addException;
  private final AddAvailabilityPauseUseCase addPause;
  private final AvailabilityPatternRepository patterns;
  private final BookingRepository bookings;
  private final DemoTutorings tutorings;
  private final Clock clock;

  DemoBookingData(
      SkillsApi skills,
      DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability,
      AddAvailabilityExceptionUseCase addException,
      AddAvailabilityPauseUseCase addPause,
      AvailabilityPatternRepository patterns,
      BookingRepository bookings,
      DemoTutorings tutorings,
      Clock clock) {
    this.skills = skills;
    this.declareWeeklyAvailability = declareWeeklyAvailability;
    this.addException = addException;
    this.addPause = addPause;
    this.patterns = patterns;
    this.bookings = bookings;
    this.tutorings = tutorings;
    this.clock = clock;
  }

  // Before the history of the demo profile, which books in these hours.
  @EventListener
  @Order(10)
  void tutorsWithHoursToBook(ApplicationReadyEvent ready) {
    for (Tutor tutor : TUTORS) {
      try {
        TenantContext.runAs(tutor.person().university(), () -> fill(tutor));
      } catch (RuntimeException failure) {
        // Demonstration data must not keep the application from starting: the rest of dev works
        // without a tutor, and the reason is right here in the log.
        log.error("Could not give the demo tutor {} their hours", tutor.person().id(), failure);
      }
    }
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      try {
        TenantContext.runAs(university, () -> book(university));
      } catch (RuntimeException failure) {
        log.error("Could not book the demo tutorings of {}", university, failure);
      }
    }
  }

  private void fill(Tutor tutor) {
    String tenant = tutor.person().university();
    UUID id = tutor.person().id();
    if (!patterns.findByTenantIdAndTutorId(tenant, id).isEmpty()) {
      return;
    }
    List<UUID> teaches = skills.enabledSkillsOf(id);
    if (teaches.isEmpty()) {
      log.warn("The demo tutor {} of {} teaches nothing: no hours", id, tenant);
      return;
    }

    LocalDate today = LocalDate.now(clock.withZone(LIMA));
    LocalDate validFrom = today.minusWeeks(8);
    int hours = 0;
    for (DayOfWeek day : DayOfWeek.values()) {
      if (tutor.week().days.contains(day)) {
        hours +=
            declareWeeklyAvailability
                .execute(id, day, tutor.week().from, tutor.week().to, validFrom, null)
                .generation()
                .blocksCreated();
      }
    }

    if (tutor.week() == Week.WEEKDAY_MORNINGS) {
      // A weekday off, past the tutorings booked below.
      LocalDate dayOff = today.plusDays(8);
      while (!WEEKDAYS.contains(dayOff.getDayOfWeek())) {
        dayOff = dayOff.plusDays(1);
      }
      addException.execute(id, dayOff, tutor.week().from, tutor.week().to, ExceptionKind.REMOVE);
    }
    if (tutor.week() == Week.WEEKENDS) {
      // A week away, past the tutorings booked below.
      addPause.execute(id, today.plusDays(14), today.plusDays(20));
    }

    log.info(
        "Demo tutor ready in {}: {} teaches {} courses and has {} bookable hours, {}",
        tenant,
        id,
        teaches.size(),
        hours,
        tutor.week());
  }

  private void book(String university) {
    Instant now = clock.instant();
    if (!bookings.existsByTenantIdAndStatusAndStartsAtAfter(
        university, BookingStatus.CONFIRMED, now)) {
      int booked = 0;
      for (Upcoming upcoming : UPCOMING) {
        if (upcoming.student().university().equals(university) && bookUpcoming(upcoming, now)) {
          booked++;
        }
      }
      log.info("Demo tutorings of {}: {} upcoming booked", university, booked);
    }

    if (!bookings.existsByTenantIdAndStatus(university, BookingStatus.CANCELLED)) {
      for (Upcoming cancelled : CANCELLED) {
        if (cancelled.student().university().equals(university)) {
          BookedTutoring booked =
              tutorings.bookFirstOpen(
                  cancelled.student().id(),
                  cancelled.tutor().id(),
                  now.plus(Duration.ofDays(cancelled.fromDays())),
                  1,
                  cancelled.need());
          tutorings.cancel(cancelled.student().id(), booked.bookingId());
          log.info("Demo tutoring of {} booked and cancelled: {}", university, booked.bookingId());
        }
      }
    }
  }

  /** Books one tutoring ahead; a refusal is logged and the others are still booked. */
  private boolean bookUpcoming(Upcoming upcoming, Instant now) {
    try {
      tutorings.bookFirstOpen(
          upcoming.student().id(),
          upcoming.tutor().id(),
          now.plus(Duration.ofDays(upcoming.fromDays())),
          1,
          upcoming.need());
      return true;
    } catch (RuntimeException refused) {
      log.warn(
          "Could not book the demo tutoring of {} with {}: {}",
          upcoming.student().id(),
          upcoming.tutor().id(),
          refused.getMessage());
      return false;
    }
  }
}
