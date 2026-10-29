package pe.ayni.config.demo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.ayni.shared.demo.DemoBookings;
import pe.ayni.shared.demo.DemoBookings.BookedTutoring;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.demo.DemoCast.Person;
import pe.ayni.shared.demo.DemoCourses;
import pe.ayni.shared.demo.DemoNotices;
import pe.ayni.shared.demo.DemoRatings;
import pe.ayni.shared.demo.DemoRecognition;
import pe.ayni.shared.demo.DemoSearches;
import pe.ayni.shared.demo.DemoSessions;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The last four weeks of each demonstration university, played with the clock moved back.
 *
 * <p>Nobody enters the sessions the demonstration books, so a database whose only tutorings are the
 * ones booked at startup has no past: no session to review (US12), no rating, no reputation, no
 * hours taught, no recognition request, no indicators. This plays one, in each university and in
 * order: the {@link AdjustableClock} is taken back to a few days before each tutoring, the student
 * books it, the clock goes to its start and both participants enter, to six minutes in and both
 * confirm their presence code, to its end and both end it, and then they rate each other. Every
 * step is the use case of the endpoint a person calls, or of the job that would have run at that
 * time, each one called through the port its module implements under this profile ({@code
 * pe.ayni.shared.demo}): the credits are charged and paid through the chained ledger, the audit
 * and the event registry hear everything, and the emails go to Mailpit, the presence codes
 * included.
 *
 * <p>The presence code only travels by email. The sessions module reads it where the acceptance
 * tests read it, from {@code PresenceCodeIssued} as it is published, and only under this profile;
 * nothing new is exposed over HTTP.
 *
 * <p>In each university: six sessions completed by three tutors, three hours each, with ratings
 * from five stars to three, tags and comments, and drawings on the whiteboard of two of them; one
 * abandoned because the tutor did not come (the student is refunded and told) and one because the
 * student did not come; searches of courses nobody teaches; and the three tutors ask for the
 * recognition of their hours, two requests left pending and one approved by the coordinator. At the
 * end Ana and Bruno have opened their oldest notices and left the newest unread.
 *
 * <p>The clock is put back exactly where it was before, whatever happens, and a failure is logged
 * and does not keep the application from starting. A university that already has a tutoring that
 * started before today has a past and is left alone, so a second start plays nothing.
 */
@Component
@Profile("demo")
class DemoHistoryData {

  private static final Logger log = LoggerFactory.getLogger(DemoHistoryData.class);

  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  /** How it ended: both came, the tutor did not, or the student did not. */
  private enum Outcome {
    COMPLETED,
    TUTOR_ABSENT,
    STUDENT_ABSENT
  }

  /** One tutoring of the history, from how many days ago, and what the student wrote of it. */
  private record Tutoring(
      Person student,
      Person tutor,
      int daysAgo,
      int hours,
      Outcome outcome,
      String need,
      int stars,
      Set<String> tags,
      String comment,
      boolean drawn) {}

  /** The people of one university the history is told with. */
  private record Cast(
      String university,
      Person mainTutor,
      Person morningTutor,
      Person weekendTutor,
      Person shortTutor,
      Person student,
      Person otherStudent,
      UUID coordinator,
      List<String> uncoveredCourses,
      List<Person> searchers) {}

  private static final List<Cast> CASTS =
      List.of(
          new Cast(
              DemoCast.UPC,
              DemoCast.BRUNO,
              DemoCast.MATEO_CARDENAS,
              DemoCast.DANIELA_SALAS,
              DemoCast.ISABELLA_TAPIA,
              DemoCast.ANA,
              DemoCast.MILAGROS_CASTILLO,
              DemoCast.UPC_COORDINATOR,
              List.of("SI643", "SI705", "SI427"),
              List.of(DemoCast.VALERIA_MEDINA, DemoCast.JULIAN_ARCE, DemoCast.ANDRES_ZEGARRA)),
          new Cast(
              DemoCast.USIL,
              DemoCast.MATEO,
              DemoCast.JOAQUIN_MEDINA,
              DemoCast.DANIELA_TAPIA,
              DemoCast.PIERO_DELGADO,
              DemoCast.ROCIO_PALOMINO,
              DemoCast.ROCIO_PALOMINO,
              DemoCast.USIL_COORDINATOR,
              List.of("USIL026", "USIL034", "USIL033"),
              List.of(
                  DemoCast.DIEGO_ESPINOZA, DemoCast.LUCIA_SALAZAR, DemoCast.BRUNO_CASTILLO)));

  private final AdjustableClock clock;
  private final DemoBookings bookings;
  private final DemoSessions sessions;
  private final DemoRatings ratings;
  private final DemoRecognition recognition;
  private final DemoNotices notices;
  private final DemoSearches searches;
  private final DemoCourses courses;

  DemoHistoryData(
      AdjustableClock clock,
      DemoBookings bookings,
      DemoSessions sessions,
      DemoRatings ratings,
      DemoRecognition recognition,
      DemoNotices notices,
      DemoSearches searches,
      DemoCourses courses) {
    this.clock = clock;
    this.bookings = bookings;
    this.sessions = sessions;
    this.ratings = ratings;
    this.recognition = recognition;
    this.notices = notices;
    this.searches = searches;
    this.courses = courses;
  }

  // After the tutors have their hours and the coordinators exist.
  @EventListener
  @Order(100)
  void theLastWeeks(ApplicationReadyEvent ready) {
    Duration offset = clock.offset();
    Instant now = clock.instant();
    long started = System.nanoTime();
    try {
      for (Cast cast : CASTS) {
        try {
          TenantContext.runAs(cast.university(), () -> play(cast, now));
        } catch (RuntimeException failure) {
          // Demonstration data must not keep the application from starting.
          log.error("Could not play the demo history of {}", cast.university(), failure);
        }
      }
    } finally {
      // Exactly where it was: the same offset from the real time, not the same instant.
      clock.advance(offset.minus(clock.offset()));
    }
    log.info(
        "Demo history done in {} ms; the clock is back at {}",
        Duration.ofNanos(System.nanoTime() - started).toMillis(),
        clock.offset());
  }

  private void play(Cast cast, Instant now) {
    LocalDate today = LocalDate.ofInstant(now, LIMA);
    if (bookings.anyStartingBefore(today.atStartOfDay(LIMA).toInstant())) {
      return;
    }

    for (Tutoring tutoring : tutoringsOf(cast)) {
      play(tutoring, today);
    }
    searchUncovered(cast, today);

    // The three tutors ask for their hours, and the coordinator decides the first request.
    try {
      at(today.minusDays(4).atTime(19, 0).atZone(LIMA).toInstant());
      UUID approved = recognition.request(cast.mainTutor().id());
      recognition.request(cast.morningTutor().id());
      recognition.request(cast.weekendTutor().id());
      at(today.minusDays(2).atTime(10, 0).atZone(LIMA).toInstant());
      recognition.resolve(
          cast.coordinator(),
          approved,
          true,
          "Tres horas enseñadas con buenas calificaciones, como pide la regla vigente.");
    } catch (RuntimeException refused) {
      log.warn("Could not play the demo recognition of {}: {}", cast.university(), refused.getMessage());
    }

    at(now);
    if (DemoCast.UPC.equals(cast.university())) {
      notices.markOldestRead(DemoCast.ANA.id(), 4);
      notices.markOldestRead(DemoCast.BRUNO.id(), 4);
    }
    log.info("Demo history of {} played", cast.university());
  }

  /**
   * The same eight tutorings in each university. The days are the earliest each may start; each one
   * takes the first free hours of its tutor from then, in the tutor's week.
   */
  private static List<Tutoring> tutoringsOf(Cast cast) {
    return List.of(
        new Tutoring(cast.student(), cast.mainTutor(), 30, 2, Outcome.COMPLETED,
            "Formas normales y dependencias funcionales para el parcial.",
            5, Set.of("Explica claro", "Paciente"),
            "Me ayudó muchísimo, por fin entendí la tercera forma normal.", true),
        new Tutoring(cast.otherStudent(), cast.morningTutor(), 27, 2, Outcome.COMPLETED,
            "No entiendo la herencia ni cuándo usar una interfaz.",
            5, Set.of("Domina el tema", "Buenos ejemplos"),
            "Muy buena explicación, con ejemplos de examen.", true),
        new Tutoring(cast.weekendTutor(), cast.morningTutor(), 24, 1, Outcome.COMPLETED,
            "Repasar recursión antes de la práctica.",
            4, Set.of("Puntual"),
            "Bien, aunque fue un poco rápido.", false),
        new Tutoring(cast.otherStudent(), cast.mainTutor(), 18, 1, Outcome.COMPLETED,
            "Consultas SQL con varias tablas.",
            4, Set.of("Paciente"),
            "Resolvimos todas mis dudas.", false),
        new Tutoring(cast.student(), cast.weekendTutor(), 15, 2, Outcome.COMPLETED,
            "Estadística: intervalos de confianza.",
            5, Set.of("Explica claro", "Buenos ejemplos"),
            "Excelente, muy ordenada.", false),
        new Tutoring(cast.shortTutor(), cast.weekendTutor(), 12, 1, Outcome.COMPLETED,
            "Ejercicios de estructuras de datos.",
            3, Set.of("Llegó tarde"),
            "Aprendí, pero empezamos tarde y la conexión se cortó dos veces.", false),
        new Tutoring(cast.student(), cast.shortTutor(), 9, 1, Outcome.TUTOR_ABSENT,
            "Ayuda con el informe del trabajo final.",
            0, Set.of(), null, false),
        new Tutoring(cast.otherStudent(), cast.mainTutor(), 6, 1, Outcome.STUDENT_ABSENT,
            "Repaso general antes del examen final.",
            0, Set.of(), null, false));
  }

  private void play(Tutoring tutoring, LocalDate today) {
    Instant earliest = today.minusDays(tutoring.daysAgo()).atStartOfDay(LIMA).toInstant();
    UUID student = tutoring.student().id();
    UUID tutor = tutoring.tutor().id();
    try {
      // Booked two days before the earliest day it may take place.
      at(earliest.minus(Duration.ofDays(2)));
      BookedTutoring booked =
          bookings.bookFirstOpen(student, tutor, earliest, tutoring.hours(), tutoring.need());
      UUID session = sessions.sessionOf(booked.bookingId());
      Instant start = booked.startsAt();

      at(start.minus(Duration.ofMinutes(5)));
      if (tutoring.outcome() != Outcome.TUTOR_ABSENT) {
        sessions.join(session, tutor);
      }
      if (tutoring.outcome() != Outcome.STUDENT_ABSENT) {
        sessions.join(session, student);
      }
      if (tutoring.outcome() != Outcome.COMPLETED) {
        // Ten minutes in, whoever is missing is absent: what the job would do.
        at(start.plus(Duration.ofMinutes(11)));
        sessions.abandonIfAbsent(session);
        return;
      }

      at(start.plus(Duration.ofMinutes(6)));
      sessions.confirmPresence(session);
      if (tutoring.drawn()) {
        at(start.plus(Duration.ofMinutes(20)));
        draw(session, tutor, student);
      }

      at(booked.endsAt().minus(Duration.ofMinutes(3)));
      sessions.end(session, student);
      sessions.end(session, tutor);

      at(booked.endsAt().plus(Duration.ofMinutes(30)));
      ratings.rateTutor(session, student, tutoring.stars(), tutoring.tags(), tutoring.comment());
      ratings.rateStudent(
          session, tutor, tutoring.stars() > 3, tutoring.stars() > 3, true,
          tutoring.stars() > 3 ? "Llegó con las dudas preparadas." : null);
    } catch (RuntimeException failure) {
      // One tutoring that cannot be played does not stop the others.
      log.warn(
          "Could not play the demo tutoring of {} with {} from {}: {}",
          student,
          tutor,
          earliest,
          failure.getMessage());
    }
  }

  /** What the tutor wrote on the whiteboard, and what the student added. */
  private void draw(UUID session, UUID tutor, UUID student) {
    sessions.draw(
        session, tutor, "title", "text",
        Map.of("text", "Resumen de la sesión", "x", 40, "y", 40, "color", "#1F2937"));
    sessions.draw(
        session, tutor, "steps", "text",
        Map.of(
            "text", "1. Identificar el problema\n2. Plantear un ejemplo\n3. Resolverlo juntos",
            "x", 40, "y", 100, "color", "#1F2937"));
    sessions.draw(
        session, student, "underline", "stroke",
        Map.of(
            "points", List.of(List.of(40, 70), List.of(260, 70)),
            "color", "#E42322",
            "width", 3));
    sessions.draw(
        session, student, "question", "text",
        Map.of("text", "¿Y si el caso tiene dos excepciones?", "x", 40, "y", 220, "color", "#2563EB"));
  }

  /**
   * Students look for the courses nobody teaches, in the week ahead of the moment they look: the
   * empty answers are the demand the coordinator's indicators count.
   */
  private void searchUncovered(Cast cast, LocalDate today) {
    List<UUID> uncovered = new ArrayList<>();
    for (String code : cast.uncoveredCourses()) {
      courses.courseCoded(code).ifPresent(uncovered::add);
    }
    int searched = 0;
    for (int daysAgo : List.of(20, 13, 8, 3)) {
      Instant when = today.minusDays(daysAgo).atTime(21, 0).atZone(LIMA).toInstant();
      at(when);
      for (int i = 0; i < uncovered.size(); i++) {
        Person searcher = cast.searchers().get((i + daysAgo) % cast.searchers().size());
        try {
          searches.search(
              searcher.id(), uncovered.get(i), when, when.plus(Duration.ofDays(7)));
          searched++;
        } catch (RuntimeException refused) {
          log.warn("Could not search a demo course of {}: {}", cast.university(), refused.getMessage());
        }
      }
    }
    log.info("Demo searches of {}: {} without a tutor", cast.university(), searched);
  }

  /** Puts the clock at that instant, forwards or backwards; from there it keeps running. */
  private void at(Instant instant) {
    clock.setTo(instant);
  }
}
