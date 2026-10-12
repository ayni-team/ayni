package pe.ayni.recognition.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.AlertView;
import pe.ayni.audit.AuditApi;
import pe.ayni.recognition.application.SessionAlerts.Alert;

/** What the adapter hands recognition: the open alerts of the audit, in recognition's words. */
class AuditAlertsTest {

  private final AuditApi audit = mock(AuditApi.class);
  private final AuditAlerts alerts = new AuditAlerts(audit);

  @Test
  @DisplayName("It turns each open alert of the audit into an alert of the review")
  void translatesTheAlerts() {
    UUID flagged = UUID.randomUUID();
    UUID clean = UUID.randomUUID();
    when(audit.openAlertsOf(List.of(flagged, clean)))
        .thenReturn(
            Map.of(
                flagged,
                List.of(
                    new AlertView(
                        UUID.randomUUID(),
                        "SHORT_DURATION",
                        "HIGH",
                        "A completed session lasted 5 of the 60 minutes that were booked",
                        Instant.parse("2026-10-12T15:00:00Z")))));

    Map<UUID, List<Alert>> found = alerts.alertsOf(List.of(flagged, clean));

    assertThat(found).containsOnlyKeys(flagged);
    assertThat(found.get(flagged))
        .containsExactly(
            new Alert(
                "SHORT_DURATION",
                "HIGH",
                "A completed session lasted 5 of the 60 minutes that were booked"));
  }

  @Test
  @DisplayName("Sessions the audit has nothing about carry no alert")
  void nothingToSay() {
    UUID session = UUID.randomUUID();
    when(audit.openAlertsOf(List.of(session))).thenReturn(Map.of());

    assertThat(alerts.alertsOf(List.of(session))).isEmpty();
  }
}
