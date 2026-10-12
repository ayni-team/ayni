package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/** US44: what skills keeps from a session that was taught. */
class CatalogUsageListenersTest {

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final TaughtSessionRepository taughtSessions = mock(TaughtSessionRepository.class);
  private final CatalogUsageListeners listeners = new CatalogUsageListeners(taughtSessions);

  @Test
  @DisplayName("a completed session is recorded against the item it was taught on, in its university")
  void aCompletedSessionIsRecordedAgainstTheItem() {
    UUID session = UUID.randomUUID();
    UUID tutor = UUID.randomUUID();
    UUID item = UUID.randomUUID();
    List<String> boundTo = new ArrayList<>();
    when(taughtSessions.recordIfNew(any(), any(), any(), any(), any()))
        .thenAnswer(
            call -> {
              boundTo.add(TenantContext.get());
              return 1;
            });

    listeners.on(
        new SessionCompleted(
            "UTEC", session, UUID.randomUUID(), tutor, UUID.randomUUID(), item, Credits.of(1), NOW));

    verify(taughtSessions).recordIfNew(session, "UTEC", item, tutor, NOW);
    assertThat(boundTo).containsExactly("UTEC");
  }
}
