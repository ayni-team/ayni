package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * The table behind US42 against a real PostgreSQL.
 *
 * <p>The unit test of the entity cannot tell whether the migration and the mapping agree, nor
 * whether the database refuses what the model says it must.
 *
 * <p>Configured like {@code ValidationRequestDatabaseTest} so both share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkillProposalDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

  private final UUID student = UUID.randomUUID();

  @Autowired private CategoryRepository categories;
  @Autowired private SkillProposalRepository proposals;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private static String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private UUID category() {
    return categories.save(new Category(UUID.randomUUID(), "Category " + unique(), (short) 0)).getId();
  }

  private SkillProposal propose(String tenant, UUID proposedBy, UUID category, String name) {
    return proposals.save(
        SkillProposal.propose(UUID.randomUUID(), tenant, proposedBy, category, name, "A tool", NOW));
  }

  @Test
  @DisplayName("a proposal is saved and read back")
  void aProposalIsSavedAndReadBack() {
    UUID category = category();
    SkillProposal saved = propose(UPC, student, category, "Figma " + unique());

    SkillProposal read = proposals.findByTenantIdAndId(UPC, saved.getId()).orElseThrow();

    assertThat(read.getName()).isEqualTo(saved.getName());
    assertThat(read.getDescription()).isEqualTo("A tool");
    assertThat(read.getCategoryId()).isEqualTo(category);
    assertThat(read.getProposedBy()).isEqualTo(student);
    assertThat(read.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(read.getResolvedBy()).isNull();
    assertThat(read.getCatalogItemId()).isNull();
  }

  @Test
  @DisplayName("a student reads their own proposals, newest first, and nobody else's")
  void aStudentReadsTheirOwnProposalsNewestFirst() {
    UUID category = category();
    UUID other = UUID.randomUUID();
    proposals.save(
        SkillProposal.propose(UUID.randomUUID(), UPC, other, category, "Other " + unique(), null, NOW));
    SkillProposal first =
        proposals.save(
            SkillProposal.propose(
                UUID.randomUUID(), UPC, student, category, "First " + unique(), null, NOW));
    SkillProposal second =
        proposals.save(
            SkillProposal.propose(
                UUID.randomUUID(), UPC, student, category, "Second " + unique(), null, NOW.plusSeconds(60)));

    assertThat(proposals.findByTenantIdAndProposedByOrderByCreatedAtDesc(UPC, student))
        .extracting(SkillProposal::getId)
        .containsExactly(second.getId(), first.getId());
  }

  @Test
  @DisplayName("a proposal is not visible from another university")
  void aProposalIsNotVisibleFromAnotherUniversity() {
    SkillProposal saved = propose(UPC, student, category(), "Figma " + unique());

    assertThat(proposals.findByTenantIdAndId(UTEC, saved.getId())).isEmpty();
    assertThat(proposals.findByTenantIdAndProposedByOrderByCreatedAtDesc(UTEC, student)).isEmpty();
  }

  @Test
  @DisplayName("a waiting proposal is found whatever the case of the name")
  void aWaitingProposalIsFoundWhateverTheCaseOfTheName() {
    String name = "Figma " + unique();
    propose(UPC, student, category(), name);

    assertThat(
            proposals.existsByTenantIdAndProposedByAndStatusAndNameIgnoreCase(
                UPC, student, ProposalStatus.PROPOSED, name.toUpperCase()))
        .isTrue();
    assertThat(
            proposals.existsByTenantIdAndProposedByAndStatusAndNameIgnoreCase(
                UTEC, student, ProposalStatus.PROPOSED, name))
        .isFalse();
  }

  @Test
  @DisplayName("the database refuses the same proposal waiting twice for one student")
  void theDatabaseRefusesTheSameProposalWaitingTwice() {
    UUID category = category();
    String name = "Figma " + unique();
    propose(UPC, student, category, name);

    assertThatThrownBy(() -> propose(UPC, student, category, name.toUpperCase()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("another student, or another university, may propose the same name")
  void anotherStudentOrUniversityMayProposeTheSameName() {
    UUID category = category();
    String name = "Figma " + unique();
    propose(UPC, student, category, name);

    assertThat(propose(UPC, UUID.randomUUID(), category, name)).isNotNull();
    assertThat(propose(UTEC, student, category, name)).isNotNull();
  }

  @Test
  @DisplayName("a decided proposal frees the name for a new one")
  void aDecidedProposalFreesTheName() {
    UUID category = category();
    String name = "Figma " + unique();
    SkillProposal first = propose(UPC, student, category, name);
    jdbc.update(
        "update skills.skill_proposals set status = 'REJECTED', resolved_by = ?, resolved_at = now() where id = ?",
        UUID.randomUUID(),
        first.getId());

    assertThat(propose(UPC, student, category, name)).isNotNull();
  }

  @Test
  @DisplayName("the database refuses a decided proposal that has no moderator")
  void theDatabaseRefusesADecidedProposalWithNoModerator() {
    SkillProposal saved = propose(UPC, student, category(), "Figma " + unique());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update skills.skill_proposals set status = 'APPROVED' where id = ?",
                    saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the database refuses a status the model does not know")
  void theDatabaseRefusesAStatusTheModelDoesNotKnow() {
    SkillProposal saved = propose(UPC, student, category(), "Figma " + unique());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update skills.skill_proposals set status = 'MAYBE' where id = ?", saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the database refuses a proposal for a category that does not exist")
  void theDatabaseRefusesAProposalForAMissingCategory() {
    assertThatThrownBy(() -> propose(UPC, student, UUID.randomUUID(), "Figma " + unique()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
