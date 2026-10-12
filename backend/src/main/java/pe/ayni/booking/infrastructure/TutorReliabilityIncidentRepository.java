package pe.ayni.booking.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.booking.domain.model.TutorReliabilityIncident;

/** Persists the incidents that affect a tutor's reliability history. */
public interface TutorReliabilityIncidentRepository
    extends JpaRepository<TutorReliabilityIncident, UUID> {}
