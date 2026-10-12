package pe.ayni.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyAlreadyResolved;
import pe.ayni.audit.domain.model.AnomalyKind;
import pe.ayni.audit.domain.model.AnomalyStatus;
import pe.ayni.audit.domain.model.InvalidDismissalReason;
import pe.ayni.audit.domain.model.Severity;

/** The rules of an anomaly, without Spring. */
class AnomalyTest {

  private static final Instant AT = Instant.parse("2026-10-12T15:00:00Z");

  private final UUID tutor = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final UUID coordinator = UUID.randomUUID();

  private Anomaly open() {
    return Anomaly.detected(
        UUID.randomUUID(),
        "UPC",
        AnomalyKind.REPEATED_PAIR,
        tutor,
        Severity.MEDIUM,
        Map.of("tutorId", tutor.toString(), "studentId", student.toString(), "sessions", "4"),
        AT);
  }

  @Test
  @DisplayName("A detected anomaly is open and has nobody who decided it")
  void startsOpen() {
    Anomaly anomaly = open();

    assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.OPEN);
    assertThat(anomaly.getResolvedBy()).isNull();
    assertThat(anomaly.getResolvedAt()).isNull();
    assertThat(anomaly.getResolutionReason()).isNull();
  }

  @Test
  @DisplayName("Dismissing it keeps who decided, when and why")
  void dismissing() {
    Anomaly anomaly = open();

    anomaly.dismiss(coordinator, "  They are study partners  ", AT.plusSeconds(60));

    assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.DISMISSED);
    assertThat(anomaly.getResolvedBy()).isEqualTo(coordinator);
    assertThat(anomaly.getResolvedAt()).isEqualTo(AT.plusSeconds(60));
    assertThat(anomaly.getResolutionReason()).isEqualTo("They are study partners");
  }

  @Test
  @DisplayName("A decision is not taken twice")
  void decidedOnce() {
    Anomaly anomaly = open();
    anomaly.dismiss(coordinator, "Reviewed", AT);

    assertThatThrownBy(() -> anomaly.dismiss(UUID.randomUUID(), "Again", AT.plusSeconds(1)))
        .isInstanceOf(AnomalyAlreadyResolved.class);
    assertThat(anomaly.getResolvedBy()).isEqualTo(coordinator);
    assertThat(anomaly.getResolutionReason()).isEqualTo("Reviewed");
  }

  @Test
  @DisplayName("A reason is required, and it has a limit")
  void reasonIsRequired() {
    Anomaly anomaly = open();

    assertThatThrownBy(() -> anomaly.dismiss(coordinator, null, AT)).isInstanceOf(InvalidDismissalReason.class);
    assertThatThrownBy(() -> anomaly.dismiss(coordinator, "   ", AT)).isInstanceOf(InvalidDismissalReason.class);
    assertThatThrownBy(() -> anomaly.dismiss(coordinator, "x".repeat(501), AT))
        .isInstanceOf(InvalidDismissalReason.class);
    assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.OPEN);

    anomaly.dismiss(coordinator, "x".repeat(500), AT);
    assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.DISMISSED);
  }

  @Test
  @DisplayName("The alert of a session and a kind has one identity, and another university has its own")
  void derivedIdentity() {
    UUID session = UUID.randomUUID();

    assertThat(Anomaly.idFor("UPC", AnomalyKind.FAILED_PRESENCE, session))
        .isEqualTo(Anomaly.idFor("UPC", AnomalyKind.FAILED_PRESENCE, session))
        .isNotEqualTo(Anomaly.idFor("UPC", AnomalyKind.SHORT_DURATION, session))
        .isNotEqualTo(Anomaly.idFor("UPCH", AnomalyKind.FAILED_PRESENCE, session))
        .isNotEqualTo(Anomaly.idFor("UPC", AnomalyKind.FAILED_PRESENCE, UUID.randomUUID()));
  }

  @Test
  @DisplayName("It involves the subject and whoever its detail names, each with their role")
  void involvedPeople() {
    assertThat(open().involvedPeople()).containsOnly(Map.entry(tutor, "TUTOR"), Map.entry(student, "STUDENT"));

    Anomaly noDetail =
        Anomaly.detected(
            UUID.randomUUID(), "UPC", AnomalyKind.FAILED_PRESENCE, tutor, Severity.LOW, null, AT);
    assertThat(noDetail.involvedPeople()).containsOnly(Map.entry(tutor, "TUTOR"));
    assertThat(noDetail.getDetail()).isEmpty();
  }

  @Test
  @DisplayName("It says what was found in words")
  void description() {
    assertThat(open().description())
        .isEqualTo("The same tutor and student completed 4 sessions in the last few days, more than the expected that is usual");

    Anomaly shortOne =
        Anomaly.detected(
            UUID.randomUUID(),
            "UPC",
            AnomalyKind.SHORT_DURATION,
            tutor,
            Severity.HIGH,
            Map.of("bookedMinutes", "60", "actualMinutes", "5"),
            AT);
    assertThat(shortOne.description()).isEqualTo("A completed session lasted 5 of the 60 minutes that were booked");
  }

  @Test
  @DisplayName("An anomaly belongs to a university")
  void belongsToAUniversity() {
    assertThatThrownBy(
            () ->
                Anomaly.detected(
                    UUID.randomUUID(), " ", AnomalyKind.REPEATED_PAIR, tutor, Severity.LOW, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
