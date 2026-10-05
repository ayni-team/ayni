package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * US16, over HTTP, against a real database and a real folder for the files.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US16-evidence-for-a-tool.feature},
 * with the same names. The identity of each person is stubbed, since identity is another module;
 * everything else is the real application.
 *
 * <p>What was published is read from the events recorded in the test's thread, which is the one
 * MockMvc runs the request in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class EvidenceValidationAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Path EVIDENCE_DIR = newEvidenceFolder();
  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";
  private static final byte[] PDF = "%PDF-1.7 the portfolio of a tutor".getBytes(StandardCharsets.UTF_8);

  @DynamicPropertySource
  static void evidenceFolder(DynamicPropertyRegistry registry) {
    registry.add("ayni.skills.evidence-dir", EVIDENCE_DIR::toString);
  }

  /** Each scenario has a tutor and a coordinator of its own. */
  private final UUID tutor = UUID.randomUUID();

  private final UUID coordinator = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @Autowired private ValidationRequestRepository requests;
  @Autowired private EvidenceFileRepository evidenceFiles;
  @Autowired private ApplicationEvents applicationEvents;
  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void theCoordinatorIsTheOnlyCoordinator() {
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              boolean coordinatorRole = id.equals(coordinator);
              return new UserView(
                  id,
                  UPC,
                  coordinatorRole ? UserRole.COORDINATOR : UserRole.STUDENT,
                  "someone@upc.edu.pe",
                  coordinatorRole ? null : "U202310949",
                  coordinatorRole ? "Carmen Rojas" : "Ana Torres",
                  coordinatorRole ? null : "Software Engineering",
                  coordinatorRole ? null : "5",
                  null);
            });
  }

  @Test
  @DisplayName("Submitting the evidence")
  void submittingTheEvidence() throws Exception {

    CatalogItem tool = seedTool();
    long storedBefore = storedFiles();

    String body = submitted(tutor, tool, pdf("portfolio.pdf"));

    assertThat(read(body, "$.skillStatus", String.class)).isEqualTo("PENDING");
    assertThat(read(body, "$.requestStatus", String.class)).isEqualTo("SUBMITTED");
    assertThat(read(body, "$.files[0].fileName", String.class)).isEqualTo("portfolio.pdf");
    assertThat(storedFiles()).isEqualTo(storedBefore + 1);

    String requestId = read(body, "$.validationRequestId", String.class);
    String queue = queue(coordinator, UPC);
    assertThat(read(queue, queueEntry(requestId) + ".tutor.fullName", List.class)).containsExactly("Ana Torres");
    assertThat(read(queue, queueEntry(requestId) + ".tool.name", List.class)).containsExactly(tool.getName());
  }

  @Test
  @DisplayName("Approved evidence")
  void approvedEvidence() throws Exception {

    CatalogItem tool = seedTool();
    String requestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);

    String decision =
        decide(coordinator, requestId, "APPROVE", "Solid portfolio").andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString();

    assertThat(read(decision, "$.requestStatus", String.class)).isEqualTo("APPROVED");
    assertThat(read(decision, "$.skillStatus", String.class)).isEqualTo("ENABLED");

    String skills = skillsOf(tutor);
    assertThat(read(skills, "$[0].status", String.class)).isEqualTo("ENABLED");
    assertThat(read(skills, "$[0].accreditationPath", String.class)).isEqualTo("REVIEWED_EVIDENCE");

    ValidationRequest decided = requests.findByTenantIdAndId(UPC, UUID.fromString(requestId)).orElseThrow();
    assertThat(decided.getStatus()).isEqualTo(ValidationStatus.APPROVED);
    assertThat(decided.getReviewedBy()).isEqualTo(coordinator);
    assertThat(decided.getReviewedAt()).isNotNull();
    assertThat(evidenceFiles.findByTenantIdAndValidationRequestId(UPC, decided.getId())).hasSize(1);

    assertThat(read(queue(coordinator, UPC), queueEntry(requestId), List.class)).isEmpty();
    assertThat(resolvedEventsOf(requestId))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.approved()).isTrue();
              assertThat(event.tutorId()).isEqualTo(tutor);
              assertThat(event.catalogItemId()).isEqualTo(tool.getId());
            });
    assertThat(enabledEventsOf(tool)).hasSize(1);
  }

  @Test
  @DisplayName("Rejected evidence")
  void rejectedEvidence() throws Exception {

    CatalogItem tool = seedTool();
    String firstRequestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);

    String decision =
        decide(coordinator, firstRequestId, "REJECT", "The certificate is not legible")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(read(decision, "$.skillStatus", String.class)).isEqualTo("REJECTED");

    String skills = skillsOf(tutor);
    assertThat(read(skills, "$[0].status", String.class)).isEqualTo("REJECTED");
    assertThat(read(skills, "$[0].review.decisionReason", String.class)).isEqualTo("The certificate is not legible");
    assertThat(enabledEventsOf(tool)).isEmpty();

    String again = submitted(tutor, tool, pdf("better.pdf"));
    String secondRequestId = read(again, "$.validationRequestId", String.class);

    assertThat(read(again, "$.skillStatus", String.class)).isEqualTo("PENDING");
    assertThat(secondRequestId).isNotEqualTo(firstRequestId);
    String afterwards = skillsOf(tutor);
    assertThat(read(afterwards, "$", List.class)).hasSize(1);
    assertThat(read(afterwards, "$[0].status", String.class)).isEqualTo("PENDING");
    assertThat(read(afterwards, "$[0].review.status", String.class)).isEqualTo("SUBMITTED");
    assertThat(read(afterwards, "$[0].review.decisionReason", Object.class)).isNull();
    assertThat(requests.findByTenantIdAndId(UPC, UUID.fromString(firstRequestId)).orElseThrow().getStatus())
        .isEqualTo(ValidationStatus.REJECTED);
  }

  @Test
  @DisplayName("Following the review")
  void followingTheReview() throws Exception {

    CatalogItem tool = seedTool();
    submitted(tutor, tool, pdf("portfolio.pdf"));

    String skills = skillsOf(tutor);

    assertThat(read(skills, "$[0].status", String.class)).isEqualTo("PENDING");
    assertThat(read(skills, "$[0].review.status", String.class)).isEqualTo("SUBMITTED");
    assertThat(read(skills, "$[0].review.submittedAt", String.class)).isNotBlank();
  }

  @Test
  @DisplayName("A course cannot be validated by evidence")
  void aCourseCannotBeValidatedByEvidence() throws Exception {

    CatalogItem course = seedCourse();
    long storedBefore = storedFiles();

    submit(tutor, course, pdf("portfolio.pdf")).andExpect(status().isBadRequest());

    assertThat(storedFiles()).isEqualTo(storedBefore);
    assertThat(read(skillsOf(tutor), "$", List.class)).isEmpty();
  }

  @Test
  @DisplayName("Evidence of a kind that is not accepted is refused")
  void evidenceOfAKindThatIsNotAcceptedIsRefused() throws Exception {

    CatalogItem tool = seedTool();
    long storedBefore = storedFiles();
    MockMultipartFile page =
        new MockMultipartFile("files", "page.html", "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8));

    submit(tutor, tool, page).andExpect(status().isBadRequest());

    assertThat(storedFiles()).isEqualTo(storedBefore);
    assertThat(read(skillsOf(tutor), "$", List.class)).isEmpty();
  }

  @Test
  @DisplayName("A student cannot review evidence")
  void aStudentCannotReviewEvidence() throws Exception {

    CatalogItem tool = seedTool();
    String requestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);

    mockMvc
        .perform(get("/api/v1/coordinator/validations").headers(headers(tutor, UPC)))
        .andExpect(status().isForbidden());
    decide(tutor, requestId, "APPROVE", null).andExpect(status().isForbidden());

    assertThat(requests.findByTenantIdAndId(UPC, UUID.fromString(requestId)).orElseThrow().isWaiting()).isTrue();
  }

  @Test
  @DisplayName("A submission already decided cannot be decided again")
  void aSubmissionAlreadyDecidedCannotBeDecidedAgain() throws Exception {

    CatalogItem tool = seedTool();
    String requestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);
    decide(coordinator, requestId, "APPROVE", null).andExpect(status().isOk());

    decide(coordinator, requestId, "APPROVE", null).andExpect(status().isConflict());
    decide(coordinator, requestId, "REJECT", "Changed my mind").andExpect(status().isConflict());

    assertThat(resolvedEventsOf(requestId)).hasSize(1);
    assertThat(enabledEventsOf(tool)).hasSize(1);
  }

  @Test
  @DisplayName("A rejection needs a reason")
  void aRejectionNeedsAReason() throws Exception {

    CatalogItem tool = seedTool();
    String requestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);

    decide(coordinator, requestId, "REJECT", "  ").andExpect(status().isBadRequest());

    assertThat(read(queue(coordinator, UPC), queueEntry(requestId), List.class)).hasSize(1);
    assertThat(resolvedEventsOf(requestId)).isEmpty();
  }

  @Test
  @DisplayName("A coordinator opens a file of a submission")
  void aCoordinatorOpensAFileOfASubmission() throws Exception {

    CatalogItem tool = seedTool();
    String body = submitted(tutor, tool, pdf("portfolio.pdf"));
    String requestId = read(body, "$.validationRequestId", String.class);
    String fileId = read(body, "$.files[0].id", String.class);

    byte[] downloaded =
        mockMvc
            .perform(
                get("/api/v1/coordinator/validations/{id}/files/{fileId}", requestId, fileId)
                    .headers(headers(coordinator, UPC)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("attachment")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(downloaded).isEqualTo(PDF);
    mockMvc
        .perform(
            get("/api/v1/coordinator/validations/{id}/files/{fileId}", requestId, UUID.randomUUID())
                .headers(headers(coordinator, UPC)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Another university does not see the queue")
  void anotherUniversityDoesNotSeeTheQueue() throws Exception {

    CatalogItem tool = seedTool();
    String requestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);

    String otherQueue = queue(coordinator, UTEC);

    assertThat(read(otherQueue, queueEntry(requestId), List.class)).isEmpty();
    decide(coordinator, requestId, "APPROVE", null, UTEC).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A withdrawn tool with accepted evidence is offered again")
  void aWithdrawnToolWithAcceptedEvidenceIsOfferedAgain() throws Exception {

    CatalogItem tool = seedTool();
    String requestId = read(submitted(tutor, tool, pdf("portfolio.pdf")), "$.validationRequestId", String.class);
    String skillId =
        read(
            decide(coordinator, requestId, "APPROVE", null).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(),
            "$.skillId",
            String.class);
    mockMvc
        .perform(delete("/api/v1/tutor/skills/{id}", skillId).headers(headers(tutor, UPC)))
        .andExpect(status().isNoContent());

    String offered =
        mockMvc
            .perform(
                post("/api/v1/tutor/skills")
                    .headers(headers(tutor, UPC))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"catalogItemId\":\"" + tool.getId() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(read(offered, "$.id", String.class)).isEqualTo(skillId);
    assertThat(read(offered, "$.status", String.class)).isEqualTo("ENABLED");
    assertThat(read(offered, "$.accreditationPath", String.class)).isEqualTo("REVIEWED_EVIDENCE");
    assertThat(requests.findByTenantIdAndOfferedSkillIdIn(UPC, List.of(UUID.fromString(skillId)))).hasSize(1);
  }

  // ---- what each scenario needs -------------------------------------------------------------

  private HttpHeaders headers(UUID user, String tenant) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenant);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private static MockMultipartFile pdf(String name) {
    return new MockMultipartFile("files", name, "application/pdf", PDF);
  }

  private ResultActions submit(UUID user, CatalogItem item, MockMultipartFile... files) throws Exception {
    MockMultipartHttpServletRequestBuilder request =
        multipart("/api/v1/tutor/skills/{id}/validation", item.getId());
    for (MockMultipartFile file : files) {
      request.file(file);
    }
    request.param("note", "Two years of work with it");
    request.headers(headers(user, UPC));
    return mockMvc.perform(request);
  }

  /** Submits the evidence, expects it to be received, and returns the answer. */
  private String submitted(UUID user, CatalogItem item, MockMultipartFile... files) throws Exception {
    return submit(user, item, files).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }

  private ResultActions decide(UUID user, String requestId, String decision, String reason) throws Exception {
    return decide(user, requestId, decision, reason, UPC);
  }

  private ResultActions decide(UUID user, String requestId, String decision, String reason, String tenant)
      throws Exception {
    String body =
        reason == null
            ? "{\"decision\":\"%s\"}".formatted(decision)
            : "{\"decision\":\"%s\",\"reason\":\"%s\"}".formatted(decision, reason);
    return mockMvc.perform(
        post("/api/v1/coordinator/validations/{id}/decision", requestId)
            .headers(headers(user, tenant))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String queue(UUID user, String tenant) throws Exception {
    return mockMvc
        .perform(get("/api/v1/coordinator/validations").param("size", "100").headers(headers(user, tenant)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String skillsOf(UUID user) throws Exception {
    return mockMvc
        .perform(get("/api/v1/tutor/skills").headers(headers(user, UPC)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String queueEntry(String requestId) {
    return "$.items[?(@.id=='" + requestId + "')]";
  }

  private List<ValidationResolved> resolvedEventsOf(String requestId) {
    return applicationEvents.stream(ValidationResolved.class)
        .filter(event -> event.validationRequestId().toString().equals(requestId))
        .toList();
  }

  private List<SkillEnabled> enabledEventsOf(CatalogItem tool) {
    return applicationEvents.stream(SkillEnabled.class)
        .filter(event -> event.catalogItemId().equals(tool.getId()))
        .toList();
  }

  private static <T> T read(String json, String path, Class<T> type) {
    return type.cast(JsonPath.read(json, path));
  }

  private static String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private UUID seedCategory() {
    return categories.save(new Category(UUID.randomUUID(), "Category " + unique(), (short) 0)).getId();
  }

  private CatalogItem seedTool() {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, seedCategory(), "Tool " + unique(), null, null, Instant.now()));
  }

  private CatalogItem seedCourse() {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            UPC,
            seedCategory(),
            "Course " + unique(),
            null,
            "C-" + unique(),
            Instant.now()));
  }

  /** How many files the storage holds, to prove that a refusal stored nothing. */
  private static long storedFiles() {
    try (Stream<Path> files = Files.walk(EVIDENCE_DIR)) {
      return files.filter(Files::isRegularFile).count();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static Path newEvidenceFolder() {
    try {
      return Files.createTempDirectory("ayni-evidence-test");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
