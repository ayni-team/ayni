package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;

/** Every method takes the university: a proposal is never read from another one. */
public interface SkillProposalRepository extends JpaRepository<SkillProposal, UUID> {

  Optional<SkillProposal> findByTenantIdAndId(String tenantId, UUID id);

  /** What a student has proposed, newest first. */
  List<SkillProposal> findByTenantIdAndProposedByOrderByCreatedAtDesc(
      String tenantId, UUID proposedBy);

  /** Whether the student already has this proposal waiting, whatever the case of the name. */
  boolean existsByTenantIdAndProposedByAndStatusAndNameIgnoreCase(
      String tenantId, UUID proposedBy, ProposalStatus status, String name);
}
