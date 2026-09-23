package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.LearningInterest;

/** Every method takes the university: no query here may cross into another one's students. */
public interface LearningInterestRepository extends JpaRepository<LearningInterest, UUID> {

  List<LearningInterest> findByTenantIdAndStudentId(String tenantId, UUID studentId);
}
