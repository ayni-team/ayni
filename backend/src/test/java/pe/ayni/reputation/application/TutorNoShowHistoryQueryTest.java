package pe.ayni.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.reputation.TutorNoShowView;
import pe.ayni.reputation.domain.model.TutorNoShow;
import pe.ayni.reputation.infrastructure.TutorNoShowRepository;
import pe.ayni.shared.tenancy.TenantContext;

class TutorNoShowHistoryQueryTest {

  @Test
  @DisplayName("returns the current tenant's tutor no-show history")
  void returnsHistoryForTutor() {
    String tenantId = "UPC";
    UUID tutorId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID catalogItemId = UUID.randomUUID();
    Instant occurredOn = Instant.parse("2026-09-30T20:10:00Z");
    TutorNoShowRepository noShows = mock(TutorNoShowRepository.class);
    when(noShows.findByTenantIdAndTutorIdOrderByOccurredOnDesc(tenantId, tutorId))
        .thenReturn(
            List.of(
                TutorNoShow.recorded(
                    sessionId, tenantId, tutorId, catalogItemId, occurredOn)));
    TutorNoShowHistoryQuery query = new TutorNoShowHistoryQuery(noShows);

    AtomicReference<List<TutorNoShowView>> history = new AtomicReference<>();
    TenantContext.runAs(tenantId, () -> history.set(query.execute(tutorId)));

    assertThat(history.get())
        .containsExactly(new TutorNoShowView(sessionId, catalogItemId, occurredOn));
    verify(noShows).findByTenantIdAndTutorIdOrderByOccurredOnDesc(tenantId, tutorId);
  }
}
