package pe.ayni.reputation.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.reputation.domain.model.TutorNoShow;

/** Historical tutor no-shows, always read within the current university. */
public interface TutorNoShowRepository extends JpaRepository<TutorNoShow, UUID> {

  List<TutorNoShow> findByTenantIdAndTutorIdOrderByOccurredOnDesc(
      String tenantId, UUID tutorId);
}
