package pe.ayni.reputation.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.TutorNoShowView;
import pe.ayni.reputation.infrastructure.TutorNoShowRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Reads a tutor's compliance history for the current university. */
@Service
public class TutorNoShowHistoryQuery {

  private final TutorNoShowRepository noShows;

  TutorNoShowHistoryQuery(TutorNoShowRepository noShows) {
    this.noShows = noShows;
  }

  @Transactional(readOnly = true)
  public List<TutorNoShowView> execute(UUID tutorId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    return noShows
        .findByTenantIdAndTutorIdOrderByOccurredOnDesc(TenantContext.require(), tutorId)
        .stream()
        .map(
            noShow ->
                new TutorNoShowView(
                    noShow.getSessionId(), noShow.getCatalogItemId(), noShow.getOccurredOn()))
        .toList();
  }
}
