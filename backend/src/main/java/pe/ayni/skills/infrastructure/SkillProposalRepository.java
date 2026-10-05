package pe.ayni.skills.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;

/** Every method takes the university: a proposal is never read from another one. */
public interface SkillProposalRepository extends JpaRepository<SkillProposal, UUID> {

  Optional<SkillProposal> findByTenantIdAndId(String tenantId, UUID id);

  /**
   * One proposal, locked until the transaction ends.
   *
   * <p>Two moderators resolving the same proposal at once would both read it as waiting, and the
   * student would be told two things, possibly opposite ones. The second waits and then finds it
   * already resolved.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select proposal from SkillProposal proposal
      where proposal.tenantId = :tenantId and proposal.id = :id
      """)
  Optional<SkillProposal> lockByTenantIdAndId(
      @Param("tenantId") String tenantId, @Param("id") UUID id);

  /**
   * Points the proposals that ended in {@code source} at {@code target}, so the students who proposed
   * a tool read the item that stays when it was joined to another.
   *
   * @return how many proposals were pointed at {@code target}
   */
  @Modifying(flushAutomatically = true)
  @Query("update SkillProposal proposal set proposal.catalogItemId = :target where proposal.catalogItemId = :source")
  int repoint(@Param("source") UUID source, @Param("target") UUID target);

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
