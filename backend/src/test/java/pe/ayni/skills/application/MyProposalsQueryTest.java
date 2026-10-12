package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/** US42, scenario 3: the student follows what they proposed. */
class MyProposalsQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
  private static final UUID STUDENT = UUID.randomUUID();

  private final SkillProposalRepository proposals = mock(SkillProposalRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final MyProposalsQuery query = new MyProposalsQuery(proposals, categories);

  private List<ProposalView> proposalsOfStudent() {
    AtomicReference<List<ProposalView>> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(STUDENT)));
    return result.get();
  }

  @Test
  @DisplayName("a student who proposed nothing sees an empty list and no category is read")
  void aStudentWhoProposedNothingSeesAnEmptyList() {
    when(proposals.findByTenantIdAndProposedByOrderByCreatedAtDesc(UPC, STUDENT)).thenReturn(List.of());

    assertThat(proposalsOfStudent()).isEmpty();
    verify(categories, never()).findAllById(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("the proposals come in the order they were read, each with the name of its category")
  void theProposalsComeWithTheNameOfTheirCategory() {
    UUID design = UUID.randomUUID();
    UUID technology = UUID.randomUUID();
    SkillProposal newest =
        SkillProposal.propose(UUID.randomUUID(), UPC, STUDENT, design, "Figma", null, NOW.plusSeconds(60));
    SkillProposal oldest =
        SkillProposal.propose(UUID.randomUUID(), UPC, STUDENT, technology, "Rust", null, NOW);
    SkillProposal sameCategory =
        SkillProposal.propose(UUID.randomUUID(), UPC, STUDENT, design, "Canva", null, NOW);
    when(proposals.findByTenantIdAndProposedByOrderByCreatedAtDesc(UPC, STUDENT))
        .thenReturn(List.of(newest, oldest, sameCategory));
    when(categories.findAllById(ArgumentMatchers.<Iterable<UUID>>any()))
        .thenReturn(
            List.of(new Category(design, "Design", (short) 0), new Category(technology, "Technology", (short) 1)));

    List<ProposalView> read = proposalsOfStudent();

    assertThat(read).extracting(view -> view.proposal().getName()).containsExactly("Figma", "Rust", "Canva");
    assertThat(read).extracting(ProposalView::categoryName).containsExactly("Design", "Technology", "Design");
  }
}
