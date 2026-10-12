package pe.ayni.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.audit.infrastructure.ActivityLogRepository;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingCancelled.CancelledBy;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.CreditsExpired;
import pe.ayni.shared.events.CreditsGranted;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionStarted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.events.ValidationResolved;

/** What each listener writes down, with the real use case over a repository that only collects. */
class AuditEventListenersTest {

  private static final Instant AT = Instant.parse("2026-10-12T15:00:00Z");
  private static final Instant LATER = Instant.parse("2026-10-12T16:30:00Z");

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final UUID booking = UUID.randomUUID();
  private final UUID session = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();

  private final List<ActivityEntry> saved = new ArrayList<>();
  private ActivityLogRepository repository;
  private AuditEventListeners listeners;

  @BeforeEach
  void listenersOverACollectingRepository() {
    repository = mock(ActivityLogRepository.class);
    when(repository.save(any(ActivityEntry.class)))
        .thenAnswer(
            call -> {
              saved.add(call.getArgument(0));
              return call.getArgument(0);
            });
    Clock clock = Clock.fixed(LATER, ZoneOffset.UTC);
    listeners = new AuditEventListeners(new RecordActivityUseCase(repository, clock));
  }

  private ActivityEntry only() {
    assertThat(saved).hasSize(1);
    return saved.get(0);
  }

  @Test
  @DisplayName("a confirmed booking is the student's, on the booking, when the event says")
  void bookingConfirmed() {
    listeners.on(
        new BookingConfirmed(
            "UPC", booking, student, tutor, item, AT.plusSeconds(3600), AT.plusSeconds(7200), List.of(UUID.randomUUID()), Credits.of(2), AT));

    ActivityEntry entry = only();
    assertThat(entry.getTenantId()).isEqualTo("UPC");
    assertThat(entry.getActorId()).isEqualTo(student);
    assertThat(entry.getActorRole()).isEqualTo(ActorRole.STUDENT);
    assertThat(entry.getAction()).isEqualTo("BOOKING_CONFIRMED");
    assertThat(entry.getResourceType()).isEqualTo("BOOKING");
    assertThat(entry.getResourceId()).isEqualTo(booking);
    assertThat(entry.getOccurredAt()).isEqualTo(AT);
    assertThat(entry.getMetadata()).containsEntry("tutorId", tutor.toString()).containsEntry("creditsCharged", "2");
  }

  @Test
  @DisplayName("a cancellation is the person's who cancelled, or the system's")
  void bookingCancelled() {
    listeners.on(new BookingCancelled("UPC", booking, student, tutor, List.of(), CancelledBy.TUTOR, true, AT));
    listeners.on(new BookingCancelled("UPC", booking, student, tutor, List.of(), CancelledBy.STUDENT, false, AT));
    listeners.on(new BookingCancelled("UPC", booking, student, tutor, List.of(), CancelledBy.SYSTEM, false, AT));

    assertThat(saved).extracting(ActivityEntry::getActorId).containsExactly(tutor, student, null);
    assertThat(saved).extracting(ActivityEntry::getActorRole).containsExactly(ActorRole.TUTOR, ActorRole.STUDENT, ActorRole.SYSTEM);
    assertThat(saved).extracting(ActivityEntry::getAction).containsOnly("BOOKING_CANCELLED");
    assertThat(saved.get(0).getMetadata()).containsEntry("late", "true");
  }

  @Test
  @DisplayName("what happens to a session is the system's, on the session")
  void sessions() {
    listeners.on(new SessionStarted("UPC", session, booking, AT));
    listeners.on(new SessionCompleted("UPC", session, booking, tutor, student, item, Credits.of(1), AT));
    listeners.on(new SessionUnverified("UPC", session, booking, tutor, student, List.of(student), AT));
    listeners.on(new SessionAbandoned("UPC", session, booking, tutor, student, item, true, AT));

    assertThat(saved)
        .extracting(ActivityEntry::getAction)
        .containsExactly("SESSION_STARTED", "SESSION_COMPLETED", "SESSION_UNVERIFIED", "SESSION_ABANDONED");
    assertThat(saved).extracting(ActivityEntry::getActorRole).containsOnly(ActorRole.SYSTEM);
    assertThat(saved).extracting(ActivityEntry::getActorId).containsOnlyNulls();
    assertThat(saved).extracting(ActivityEntry::getResourceType).containsOnly("SESSION");
    assertThat(saved).extracting(ActivityEntry::getResourceId).containsOnly(session);
    assertThat(saved.get(1).getMetadata()).containsEntry("creditsEarned", "1");
    assertThat(saved.get(2).getMetadata()).containsEntry("unconfirmedUserIds", "[" + student + "]");
    assertThat(saved.get(3).getMetadata()).containsEntry("studentCheckedIn", "true");
  }

  @Test
  @DisplayName("credits granted and expired are the system's, on the account of the person")
  void credits() {
    listeners.on(new CreditsGranted("UPC", student, Credits.of(5), CreditType.SEED, AT.plusSeconds(86400), AT));
    listeners.on(new CreditsExpired("UPC", student, Credits.of(3), AT));

    assertThat(saved).extracting(ActivityEntry::getAction).containsExactly("CREDITS_GRANTED", "CREDITS_EXPIRED");
    assertThat(saved).extracting(ActivityEntry::getResourceType).containsOnly("WALLET");
    assertThat(saved).extracting(ActivityEntry::getResourceId).containsOnly(student);
    assertThat(saved.get(0).getMetadata()).containsEntry("amount", "5").containsEntry("type", "SEED");
    assertThat(saved.get(1).getMetadata()).containsEntry("amount", "3");
  }

  @Test
  @DisplayName("skills enabled and withdrawn are the system's, with the tutor in the details")
  void skills() {
    listeners.on(new SkillEnabled("UPC", tutor, item, AT));
    listeners.on(new SkillWithdrawn("UPC", tutor, item, AT));

    assertThat(saved).extracting(ActivityEntry::getAction).containsExactly("SKILL_ENABLED", "SKILL_WITHDRAWN");
    assertThat(saved).extracting(ActivityEntry::getResourceId).containsOnly(item);
    assertThat(saved).extracting(e -> e.getMetadata().get("tutorId")).containsOnly(tutor.toString());
  }

  @Test
  @DisplayName("a decision is a coordinator's with the outcome in the action, and the reason stays out")
  void decisions() {
    UUID request = UUID.randomUUID();
    UUID validation = UUID.randomUUID();
    listeners.on(new RecognitionRequested("UPC", request, student, 20, AT));
    listeners.on(new RecognitionResolved("UPC", request, student, true, "Evidence is complete", AT));
    listeners.on(new RecognitionResolved("UPC", request, student, false, "Not enough hours", AT));
    listeners.on(new ValidationResolved("UPC", validation, tutor, item, true, "Certificate is valid", AT));

    assertThat(saved)
        .extracting(ActivityEntry::getAction)
        .containsExactly("RECOGNITION_REQUESTED", "RECOGNITION_APPROVED", "RECOGNITION_REJECTED", "VALIDATION_APPROVED");
    assertThat(saved).extracting(ActivityEntry::getActorRole).containsExactly(ActorRole.STUDENT, ActorRole.COORDINATOR, ActorRole.COORDINATOR, ActorRole.COORDINATOR);
    assertThat(saved.get(0).getActorId()).isEqualTo(student);
    assertThat(saved.get(1).getActorId()).isNull();
    assertThat(saved).flatExtracting(e -> e.getMetadata().values()).noneMatch(value -> value.contains("Evidence") || value.contains("Certificate") || value.contains("hours"));
  }

  @Test
  @DisplayName("the events that carry a link, a code or an address are not listened to")
  void secretsAreNotHeard() {
    List<Class<?>> heard =
        Arrays.stream(AuditEventListeners.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("on"))
            .map(Method::getParameterTypes)
            .map(types -> types[0])
            .collect(java.util.stream.Collectors.toList());

    assertThat(heard).isNotEmpty();
    assertThat(heard).doesNotContain(AccessRequested.class, PresenceCodeIssued.class, StudentActivated.class);
  }

  @Test
  @DisplayName("a failure to record is swallowed: the operation that published goes on")
  void failureIsSwallowed() {
    Mockito.reset(repository);
    when(repository.save(any(ActivityEntry.class))).thenThrow(new IllegalStateException("database is down"));

    assertThatCode(() -> listeners.on(new SessionStarted("UPC", session, booking, AT))).doesNotThrowAnyException();
    assertThatCode(() -> listeners.on(new SessionStarted(null, session, booking, AT))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an event with no moment is written with the time of the clock")
  void noMomentUsesTheClock() {
    listeners.on(new SessionStarted("UPC", session, booking, null));

    assertThat(only().getOccurredAt()).isEqualTo(LATER);
  }
}
