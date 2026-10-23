package pe.ayni.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.AvailabilityPublished;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.CoordinatorActivated;
import pe.ayni.shared.events.CreditsExpired;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.events.CreditsGranted;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.events.HoursWithdrawn;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionRated;
import pe.ayni.shared.events.SessionStarted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.events.UniversityRegistered;
import pe.ayni.shared.events.UniversitySuspended;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.shared.events.WhiteboardClosed;

/**
 * TS04: what the event publication registry writes down, without Spring.
 *
 * <p>The mapper is built the way Spring Boot builds the application's: dates as ISO 8601 text, and
 * unknown properties ignored when reading.
 */
class RedactingEventSerializerTest {

  private static final Instant AT = Instant.parse("2026-10-22T15:00:00Z");
  private static final String LINK = "http://localhost:5173/access/confirm?token=s3cr3t-t0k3n";
  private static final String EMAIL = "ana.torres@upc.edu.pe";

  private final RedactingEventSerializer serializer =
      new RedactingEventSerializer(
          Jackson2ObjectMapperBuilder.json()
              .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
              .build());

  /** One of each event of {@code shared.events}, with every field filled. */
  static Stream<Object> everyEvent() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    return Stream.of(
        new AvailabilityPublished("UPC", a, AT),
        new BookingCancelled(
            "UPC", a, b, c, List.of(a), BookingCancelled.CancelledBy.TUTOR, true, AT),
        new BookingConfirmed(
            "UPC", a, b, c, a, AT, AT.plusSeconds(3600), List.of(b, c), Credits.of(2), AT),
        new CoordinatorActivated("UPC", a, AT),
        new CreditsExpired("UPC", a, Credits.of(3), AT),
        new CreditsExpiring("UPC", a, Credits.of(3), AT.plusSeconds(86_400), AT),
        new CreditsGranted("UPC", a, Credits.of(5), CreditType.SEED, AT.plusSeconds(86_400), AT),
        new HoursGenerated("UPC", a, List.of(new HoursGenerated.Block(b, AT)), AT),
        new HoursWithdrawn("UPC", a, List.of(b), AT),
        new PurchaseConfirmed("UPC", a, b, Credits.of(4), AT),
        new RecognitionRequested("UPC", a, b, 12, AT),
        new RecognitionResolved("UPC", a, b, false, "Not enough hours", AT),
        new SessionAbandoned("UPC", a, b, c, a, b, true, AT),
        new SessionCompleted("UPC", a, b, c, a, b, Credits.of(1), AT),
        new SessionRated("UPC", a, b, c, SessionRated.Direction.STUDENT_TO_TUTOR, 5, AT),
        new SessionStarted("UPC", a, b, AT),
        new SessionUnverified("UPC", a, b, c, a, List.of(c), AT),
        new SkillEnabled("UPC", a, b, AT),
        new SkillWithdrawn("UPC", a, b, AT),
        new UniversityRegistered("UPC", "Universidad Peruana de Ciencias Aplicadas", AT),
        new UniversitySuspended("UPC", AT),
        new ValidationResolved("UPC", a, b, c, true, null, AT),
        new WhiteboardClosed("UPC", a, b, c, false, AT));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyEvent")
  @DisplayName("an event without secrets is stored whole and read back equal")
  void anEventWithoutSecretsIsStoredWhole(Object event) {
    Object stored = serializer.serialize(event);

    assertThat(serializer.deserialize(stored, event.getClass())).isEqualTo(event);
    // The registry finds a delivery it did not start by the stored text, so it must not vary.
    assertThat(serializer.serialize(event)).isEqualTo(stored);
  }

  @Test
  @DisplayName("an access link is stored without the link and without the email")
  void anAccessLinkIsStoredWithoutTheLinkNorTheEmail() {
    AccessRequested requested =
        new AccessRequested("UPC", EMAIL, "LOGIN", LINK, AT.plusSeconds(600), AT);

    String stored = serializer.serialize(requested).toString();

    assertThat(stored).doesNotContain(LINK).doesNotContain("s3cr3t-t0k3n").doesNotContain(EMAIL);
    assertThat(serializer.deserialize(stored, AccessRequested.class))
        .isEqualTo(new AccessRequested("UPC", null, "LOGIN", null, AT.plusSeconds(600), AT));
  }

  @Test
  @DisplayName("a presence code is stored without the code")
  void aPresenceCodeIsStoredWithoutTheCode() {
    UUID session = UUID.randomUUID();
    UUID participant = UUID.randomUUID();
    PresenceCodeIssued issued =
        new PresenceCodeIssued("UPC", session, participant, "482913", AT.plusSeconds(300), AT);

    String stored = serializer.serialize(issued).toString();

    assertThat(stored).doesNotContain("482913");
    assertThat(serializer.deserialize(stored, PresenceCodeIssued.class))
        .isEqualTo(
            new PresenceCodeIssued("UPC", session, participant, null, AT.plusSeconds(300), AT));
  }

  @Test
  @DisplayName("an activation is stored without the email and the student code")
  void anActivationIsStoredWithoutTheEmailNorTheCode() {
    UUID student = UUID.randomUUID();
    StudentActivated activated = new StudentActivated("UPC", student, EMAIL, "U202400001", AT);

    String stored = serializer.serialize(activated).toString();

    assertThat(stored).doesNotContain(EMAIL).doesNotContain("U202400001");
    assertThat(serializer.deserialize(stored, StudentActivated.class))
        .isEqualTo(new StudentActivated("UPC", student, null, null, AT));
  }

  @Test
  @DisplayName("redacting leaves the event in memory untouched")
  void redactingLeavesTheOriginalUntouched() {
    AccessRequested requested =
        new AccessRequested("UPC", EMAIL, "LOGIN", LINK, AT.plusSeconds(600), AT);

    serializer.serialize(requested);

    // The listener that runs right after the commit still receives the link to send.
    assertThat(requested.accessLink()).isEqualTo(LINK);
    assertThat(requested.email()).isEqualTo(EMAIL);
  }
}
