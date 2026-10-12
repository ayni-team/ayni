package pe.ayni.skills.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/** US42, scenario 3: what a student proposed and where each proposal stands. */
@Service
public class MyProposalsQuery {

  private final SkillProposalRepository proposals;
  private final CategoryRepository categories;

  MyProposalsQuery(SkillProposalRepository proposals, CategoryRepository categories) {
    this.proposals = proposals;
    this.categories = categories;
  }

  /** The student's proposals, newest first, in one read of the proposals and one of the categories. */
  @Transactional(readOnly = true)
  public List<ProposalView> of(UUID proposerId) {
    String tenantId = TenantContext.require();

    List<SkillProposal> own =
        proposals.findByTenantIdAndProposedByOrderByCreatedAtDesc(tenantId, proposerId);
    if (own.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> names =
        categories.findAllById(own.stream().map(SkillProposal::getCategoryId).distinct().toList()).stream()
            .collect(Collectors.toMap(Category::getId, Category::getName, (first, second) -> first));
    return own.stream()
        .map(proposal -> new ProposalView(proposal, names.get(proposal.getCategoryId())))
        .toList();
  }
}
