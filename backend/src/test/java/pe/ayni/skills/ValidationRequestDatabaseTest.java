package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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
import pe.ayni.skills.domain.model.AccreditationPath;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * The tables behind US16 against a real PostgreSQL.
 *
 * <p>The unit tests of the entities cannot tell whether the migration and the mapping agree, nor
 * whether the database refuses what the model says it must. This one does, and it is the reason a
 * resolved request without a reviewer is checked here and not only in {@code ValidationRequest}.
 *
 * <p>Configured like {@code SkillsApiDatabaseTest} so both share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationRequestDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

  private final UUID tutor = UUID.randomUUID();
  private final UUID coordinator = UUID.randomUUID();

  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private ValidationRequestRepository requests;
  @Autowired private EvidenceFileRepository files;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private static String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private CatalogItem globalTool() {
    UUID category =
        categories.save(new Category(UUID.randomUUID(), "Category " + unique(), (short) 0)).getId();
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, category, "Tool " + unique(), null, null, NOW));
  }

  private OfferedSkill pendingSkill() {
    return offeredSkills.save(
        OfferedSkill.requestValidation(UUID.randomUUID(), UPC, tutor, globalTool().getId(), NOW));
  }

  @Test
  @DisplayName("a submission with its evidence is saved and read back")
  void aSubmissionWithItsEvidenceIsSavedAndReadBack() {
    OfferedSkill skill = pendingSkill();
    ValidationRequest request =
        requests.save(
            ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), "Two years at work", NOW));
    files.save(
        new EvidenceFile(
            UUID.randomUUID(), UPC, request.getId(), "portfolio.pdf", "UPC/" + unique(), "application/pdf", 2048, NOW));

    ValidationRequest read = requests.findByTenantIdAndId(UPC, request.getId()).orElseThrow();
    List<EvidenceFile> evidence = files.findByTenantIdAndValidationRequestId(UPC, request.getId());

    assertThat(read.getStatus()).isEqualTo(ValidationStatus.SUBMITTED);
    assertThat(read.getStudentNote()).isEqualTo("Two years at work");
    assertThat(read.getReviewedBy()).isNull();
    assertThat(evidence).singleElement().satisfies(file -> assertThat(file.getFileName()).isEqualTo("portfolio.pdf"));
  }

  @Test
  @DisplayName("an approval is stored with who decided, and the skill is enabled by the evidence")
  void anApprovalIsStoredWithWhoDecided() {
    OfferedSkill skill = pendingSkill();
    ValidationRequest request =
        requests.save(ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), null, NOW));

    request.approve(coordinator, "Solid portfolio", NOW.plusSeconds(60));
    skill.approveByReviewedEvidence(NOW.plusSeconds(60));
    requests.save(request);
    offeredSkills.save(skill);

    ValidationRequest readRequest = requests.findByTenantIdAndId(UPC, request.getId()).orElseThrow();
    OfferedSkill readSkill = offeredSkills.findById(skill.getId()).orElseThrow();
    assertThat(readRequest.getStatus()).isEqualTo(ValidationStatus.APPROVED);
    assertThat(readRequest.getReviewedBy()).isEqualTo(coordinator);
    assertThat(readRequest.getDecisionReason()).isEqualTo("Solid portfolio");
    assertThat(readSkill.getStatus()).isEqualTo(OfferedSkillStatus.ENABLED);
    assertThat(readSkill.getAccreditationPath()).isEqualTo(AccreditationPath.REVIEWED_EVIDENCE);
  }

  @Test
  @DisplayName("the files of a request are not visible from another university")
  void theFilesOfARequestAreNotVisibleFromAnotherUniversity() {
    OfferedSkill skill = pendingSkill();
    ValidationRequest request =
        requests.save(ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), null, NOW));
    files.save(
        new EvidenceFile(
            UUID.randomUUID(), UPC, request.getId(), "certificate.png", "UPC/" + unique(), "image/png", 10, NOW));

    assertThat(requests.findByTenantIdAndId(UTEC, request.getId())).isEmpty();
    assertThat(files.findByTenantIdAndValidationRequestId(UTEC, request.getId())).isEmpty();
  }

  @Test
  @DisplayName("the database refuses a decided request that has no reviewer")
  void theDatabaseRefusesADecidedRequestWithNoReviewer() {
    OfferedSkill skill = pendingSkill();
    ValidationRequest request =
        requests.save(ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), null, NOW));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update skills.validation_requests set status = 'APPROVED' where id = ?",
                    request.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the database refuses an empty evidence file")
  void theDatabaseRefusesAnEmptyEvidenceFile() {
    OfferedSkill skill = pendingSkill();
    ValidationRequest request =
        requests.save(ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), null, NOW));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    insert into skills.evidence_files
                      (id, tenant_id, validation_request_id, file_name, storage_key, content_type, size_bytes)
                    values (?, 'UPC', ?, 'empty.pdf', 'key', 'application/pdf', 0)
                    """,
                    UUID.randomUUID(),
                    request.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
