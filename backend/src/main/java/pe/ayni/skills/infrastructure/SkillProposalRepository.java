package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;

/** Every method takes the university: a proposal is never read from another one. */
public interface SkillProposalRepository extends JpaRepository<SkillProposal, UUID> {

  Optional<SkillProposal> findByTenantIdAndId(String tenantId, UUID id);

  /** What a student has proposed, newest first. */
  List<SkillProposal> findByTenantIdAndProposedByOrderByCreatedAtDesc(
      String tenantId, UUID proposedBy);

  /** One page of the proposals of the university in the given status, for the moderator. */
  Page<SkillProposal> findByTenantIdAndStatus(
      String tenantId, ProposalStatus status, Pageable pageable);

  /** Whether the student already has this proposal waiting, whatever the case of the name. */
  boolean existsByTenantIdAndProposedByAndStatusAndNameIgnoreCase(
      String tenantId, UUID proposedBy, ProposalStatus status, String name);
}
