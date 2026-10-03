package pe.ayni.reputation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.domain.model.TutorNoShow;
import pe.ayni.reputation.infrastructure.TutorNoShowRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/** Persists tutor no-shows when the student did attend. */
@Service
class RecordTutorNoShow {

  private final TutorNoShowRepository noShows;

  RecordTutorNoShow(TutorNoShowRepository noShows) {
    this.noShows = noShows;
  }

  @Transactional
  void record(SessionAbandoned event) {
    if (!event.studentCheckedIn()) {
      return;
    }
    String tenantId = TenantContext.require();
    if (!tenantId.equals(event.tenantId())) {
      throw new IllegalStateException("No-show event belongs to a different university");
    }
    noShows.save(
        TutorNoShow.recorded(
            event.sessionId(),
            tenantId,
            event.tutorId(),
            event.catalogItemId(),
            event.occurredOn()));
  }
}
