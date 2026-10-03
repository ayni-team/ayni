package pe.ayni.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.reputation.domain.model.TutorNoShow;
import pe.ayni.reputation.infrastructure.TutorNoShowRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

class RecordTutorNoShowTest {

  private static final String TENANT = "UPC";
  private static final UUID SESSION = UUID.randomUUID();
  private static final UUID BOOKING = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID CATALOG_ITEM = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-30T20:10:00Z");

  @Test
  @DisplayName("records tutor absence only when the student checked in")
  void recordsIncidentForAttendingStudent() {
    TutorNoShowRepository noShows = mock(TutorNoShowRepository.class);
    RecordTutorNoShow useCase = new RecordTutorNoShow(noShows);

    TenantContext.runAs(TENANT, () -> useCase.record(event(true)));

    ArgumentCaptor<TutorNoShow> saved = ArgumentCaptor.forClass(TutorNoShow.class);
    verify(noShows).save(saved.capture());
    assertThat(saved.getValue().getSessionId()).isEqualTo(SESSION);
    assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
    assertThat(saved.getValue().getTutorId()).isEqualTo(TUTOR);
    assertThat(saved.getValue().getCatalogItemId()).isEqualTo(CATALOG_ITEM);
    assertThat(saved.getValue().getOccurredOn()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("does not record tutor fault if the student did not check in")
  void doesNotRecordWhenBothAreAbsent() {
    TutorNoShowRepository noShows = mock(TutorNoShowRepository.class);
    RecordTutorNoShow useCase = new RecordTutorNoShow(noShows);

    TenantContext.runAs(TENANT, () -> useCase.record(event(false)));

    verify(noShows, never()).save(any());
  }

  private static SessionAbandoned event(boolean studentCheckedIn) {
    return new SessionAbandoned(
        TENANT, SESSION, BOOKING, TUTOR, STUDENT, CATALOG_ITEM, studentCheckedIn, NOW);
  }
}
