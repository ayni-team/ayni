package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.LearningInterest;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/**
 * US44, over HTTP, against a real database and a real folder for the files.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US44-clean-up-the-catalogue.feature},
 * with the same names. The identity of each person is stubbed, since identity is another module;
 * everything else is the real application.
 *
 * <p>Each test works in a university of its own and with tools of its own, and the names it asks the
 * similar skills about are letters nobody else uses, because that comparison reads the whole
 * catalogue.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CatalogCleanUpAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Path EVIDENCE_DIR = newEvidenceFolder();
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final byte[] PDF = "%PDF-1.7 the portfolio of a tutor".getBytes(StandardCharsets.UTF_8);

  @DynamicPropertySource
  static void evidenceFolder(DynamicPropertyRegistry registry) {
    registry.add("ayni.skills.evidence-dir", EVIDENCE_DIR::toString);
  }

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private LearningInterestRepository interests;
  @Autowired private TaughtSessionRepository taughtSessions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ApplicationEvents applicationEvents;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private UUID category;
  private CatalogItem duplicate;
  private CatalogItem kept;

  /** Background: a coordinator of a university of its own, and two global tools. */
  @BeforeEach
  void aCoordinatorAndTwoTools() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    category = categories.save(new Category(UUID.randomUUID(), "Category " + tenant, (short) 0)).getId();
    Set<UUID> coordinators = Set.of(coordinator);
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              boolean moderator = coordinators.contains(id);
              return new UserView(
                  id,
                  tenant,
                  moderator ? UserRole.COORDINATOR : UserRole.STUDENT,
                  "someone@upc.edu.pe",
                  moderator ? null : "U202310949",
                  moderator ? "Carla Ríos" : "Ana Torres",
                  null,
                  null,
                  null);
            });
    duplicate = tool(uniqueName());
    kept = tool(uniqueName());
  }

  @Test
  @DisplayName("Joining duplicate skills")
  void joiningDuplicateSkills() throws Exception {

    UUID firstTutor = UUID.randomUUID();
    UUID bothTutor = UUID.randomUUID();
    enabledBy(firstTutor, duplicate);
    enabledBy(bothTutor, duplicate);
    enabledBy(bothTutor, kept);
    interests.save(new LearningInterest(UUID.randomUUID(), tenant, student, duplicate.getId(), NOW));
    UUID booking = bookingOn(duplicate);

    merge(duplicate, kept).andExpect(status().isOk());

    String firstSkills = skillsOf(firstTutor);
    assertThat(JsonPath.<List<String>>read(firstSkills, "$[*].name")).containsExactly(kept.getName());
    assertThat(JsonPath.<List<String>>read(firstSkills, "$[*].status")).containsExactly("ENABLED");
    String bothSkills = skillsOf(bothTutor);
    assertThat(JsonPath.<List<String>>read(bothSkills, "$[?(@.status == 'ENABLED')].name")).containsExactly(kept.getName());
    assertThat(JsonPath.<List<String>>read(bothSkills, "$[?(@.status == 'WITHDRAWN')].name"))
        .containsExactly(duplicate.getName());
    assertThat(applicationEvents.stream(SkillWithdrawn.class).filter(event -> event.catalogItemId().equals(duplicate.getId())))
        .extracting(SkillWithdrawn::tutorId)
        .containsExactlyInAnyOrder(firstTutor, bothTutor);
    assertThat(applicationEvents.stream(SkillEnabled.class).filter(event -> event.catalogItemId().equals(kept.getId())))
        .extracting(SkillEnabled::tutorId)
        .containsExactly(firstTutor);
    assertThat(JsonPath.<List<String>>read(catalogueOf(category), "$.items[*].id")).containsExactly(kept.getId().toString());
    assertThat(interestedIn(kept)).containsExactly(student);
    assertThat(bookingState(booking)).isEqualTo(Tuple.tuple("CONFIRMED", duplicate.getId()));
  }

  @Test
  @DisplayName("Retiring a skill")
  void retiringASkill() throws Exception {

    UUID tutor = UUID.randomUUID();
    enabledBy(tutor, duplicate);

    retire(duplicate, 1).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/tutor/skills")
                .headers(headers(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogItemId\":\"" + duplicate.getId() + "\"}"))
        .andExpect(status().isBadRequest());
    String catalogue =
        body(
            mockMvc
                .perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("q", duplicate.getName()))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].name")).isEmpty();
    String similar =
        body(
            mockMvc
                .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", tenant).param("name", duplicate.getName()))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(similar, "$[*].name")).isEmpty();
    assertThat(JsonPath.<List<String>>read(skillsOf(tutor), "$[*].status")).containsExactly("WITHDRAWN");
  }

  @Test
  @DisplayName("Offers in force when retiring a skill")
  void offersInForceWhenRetiringASkill() throws Exception {

    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID third = UUID.randomUUID();
    enabledBy(first, duplicate);
    enabledBy(second, duplicate);
    enabledBy(third, duplicate);
    UUID booking = bookingOn(duplicate);

    int affected = JsonPath.<Integer>read(body(usage(coordinator, duplicate).andExpect(status().isOk())), "$.tutorsOffering");
    assertThat(affected).isEqualTo(3);

    String refusal = body(retire(duplicate, 1).andExpect(status().isConflict()));
    assertThat(JsonPath.<String>read(refusal, "$.message")).contains("3 tutors offer this skill now, not 1");
    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isTrue();
    assertThat(applicationEvents.stream(SkillWithdrawn.class).filter(event -> event.catalogItemId().equals(duplicate.getId())))
        .isEmpty();

    String answer = body(retire(duplicate, affected).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(answer, "$.tutorsAffected")).isEqualTo(3);
    assertThat(applicationEvents.stream(SkillWithdrawn.class).filter(event -> event.catalogItemId().equals(duplicate.getId())))
        .extracting(SkillWithdrawn::tutorId)
        .containsExactlyInAnyOrder(first, second, third);
    assertThat(bookingState(booking)).isEqualTo(Tuple.tuple("CONFIRMED", duplicate.getId()));
  }

  @Test
  @DisplayName("Use of the catalogue as a criterion")
  void useOfTheCatalogueAsACriterion() throws Exception {

    enabledBy(UUID.randomUUID(), duplicate);
    enabledBy(UUID.randomUUID(), duplicate);
    for (int i = 0; i < 4; i++) {
      SessionCompleted completed =
          new SessionCompleted(
              tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), student, duplicate.getId(), Credits.of(1), NOW);
      transactions.executeWithoutResult(status -> events.publishEvent(completed));
    }
    await().atMost(Duration.ofSeconds(15)).until(() -> taughtSessions.countByCatalogItemId(duplicate.getId()) == 4);

    String used = body(usage(coordinator, duplicate).andExpect(status().isOk()));
    String unused = body(usage(coordinator, kept).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(used, "$.tutorsOffering")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(used, "$.sessionsTaught")).isEqualTo(4);
    assertThat(JsonPath.<Integer>read(unused, "$.tutorsOffering")).isZero();
    assertThat(JsonPath.<Integer>read(unused, "$.sessionsTaught")).isZero();
  }

  @Test
  @DisplayName("A student cannot clean up the catalogue")
  void aStudentCannotCleanUpTheCatalogue() throws Exception {

    UUID tutor = UUID.randomUUID();
    OfferedSkill offer = enabledBy(tutor, duplicate);

    mockMvc
        .perform(get("/api/v1/coordinator/catalog/" + duplicate.getId() + "/usage").headers(headers(student)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/coordinator/catalog/" + duplicate.getId() + "/retire")
                .headers(headers(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedTutors\":1}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/coordinator/catalog/" + duplicate.getId() + "/merge")
                .headers(headers(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"intoCatalogItemId\":\"" + kept.getId() + "\"}"))
        .andExpect(status().isForbidden());

    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isTrue();
    assertThat(offeredSkills.findById(offer.getId()).orElseThrow().getCatalogItemId()).isEqualTo(duplicate.getId());
    assertThat(offeredSkills.findById(offer.getId()).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  @DisplayName("A course and a tool cannot be joined")
  void aCourseAndAToolCannotBeJoined() throws Exception {

    CatalogItem course =
        catalogItems.save(
            new CatalogItem(
                UUID.randomUUID(),
                CatalogScope.UNIVERSITY,
                tenant,
                category,
                "Course " + UUID.randomUUID(),
                null,
                "C" + UUID.randomUUID().toString().substring(0, 8),
                NOW));
    OfferedSkill offer = enabledBy(UUID.randomUUID(), course);

    merge(course, duplicate).andExpect(status().isBadRequest());
    merge(duplicate, course).andExpect(status().isBadRequest());

    assertThat(catalogItems.findById(course.getId()).orElseThrow().isActive()).isTrue();
    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isTrue();
    assertThat(offeredSkills.findById(offer.getId()).orElseThrow().getCatalogItemId()).isEqualTo(course.getId());
  }

  @Test
  @DisplayName("Evidence waiting for a retired tool cannot be approved")
  void evidenceWaitingForARetiredToolCannotBeApproved() throws Exception {

    UUID tutor = UUID.randomUUID();
    String submission =
        body(
            mockMvc
                .perform(
                    multipart("/api/v1/tutor/skills/{id}/validation", duplicate.getId())
                        .file(new MockMultipartFile("files", "portfolio.pdf", "application/pdf", PDF))
                        .param("note", "Two years of work with it")
                        .headers(headers(tutor)))
                .andExpect(status().isCreated()));
    String requestId = JsonPath.read(submission, "$.validationRequestId");

    retire(duplicate, 0).andExpect(status().isOk());

    decide(requestId, "{\"decision\":\"APPROVE\"}").andExpect(status().isConflict());
    assertThat(JsonPath.<List<String>>read(skillsOf(tutor), "$[*].status")).containsExactly("PENDING");

    decide(requestId, "{\"decision\":\"REJECT\",\"reason\":\"The tool was retired\"}").andExpect(status().isOk());
    String skills = skillsOf(tutor);
    assertThat(JsonPath.<List<String>>read(skills, "$[*].status")).containsExactly("REJECTED");
    assertThat(JsonPath.<String>read(skills, "$[0].review.decisionReason")).isEqualTo("The tool was retired");
  }

  // ---- what each scenario needs -------------------------------------------------------------

  private HttpHeaders headers(UUID user) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenant);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions usage(UUID user, CatalogItem item) throws Exception {
    return mockMvc.perform(get("/api/v1/coordinator/catalog/" + item.getId() + "/usage").headers(headers(user)));
  }

  private ResultActions retire(CatalogItem item, long confirmedTutors) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/catalog/" + item.getId() + "/retire")
            .headers(headers(coordinator))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"confirmedTutors\":" + confirmedTutors + "}"));
  }

  private ResultActions merge(CatalogItem removed, CatalogItem into) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/catalog/" + removed.getId() + "/merge")
            .headers(headers(coordinator))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"intoCatalogItemId\":\"" + into.getId() + "\"}"));
  }

  private ResultActions decide(String requestId, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/validations/{id}/decision", requestId)
            .headers(headers(coordinator))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private String skillsOf(UUID user) throws Exception {
    return body(mockMvc.perform(get("/api/v1/tutor/skills").headers(headers(user))).andExpect(status().isOk()));
  }

  private String catalogueOf(UUID categoryId) throws Exception {
    return body(
        mockMvc
            .perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("category", categoryId.toString()))
            .andExpect(status().isOk()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  /** Letters only and unlike any other name, because the similar skills compare with the whole catalogue. */
  private static String uniqueName() {
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 12; i++) {
      letters.append((char) ('a' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
    }
    return letters.toString();
  }

  private CatalogItem tool(String name) {
    return catalogItems.save(new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, category, name, null, null, NOW));
  }

  private OfferedSkill enabledBy(UUID tutor, CatalogItem item) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), tenant, tutor, item.getId(), NOW);
    skill.approveByReviewedEvidence(NOW);
    return offeredSkills.save(skill);
  }

  /** A confirmed booking on the item, which booking owns: the test writes it only to see it stand. */
  private UUID bookingOn(CatalogItem item) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into booking.bookings
          (id, tenant_id, student_id, tutor_id, catalog_item_id, starts_at, ends_at, hours, credits_charged,
           need_description, status)
        values (?, ?, ?, ?, ?, now() + interval '2 days', now() + interval '2 days 1 hour', 1, 1, 'Help with it', 'CONFIRMED')
        """,
        id,
        tenant,
        student,
        UUID.randomUUID(),
        item.getId());
    return id;
  }

  private Tuple bookingState(UUID booking) {
    var row = jdbc.queryForMap("select status, catalog_item_id from booking.bookings where id = ?", booking);
    return Tuple.tuple(row.get("status"), row.get("catalog_item_id"));
  }

  private List<UUID> interestedIn(CatalogItem item) {
    return jdbc.queryForList(
        "select student_id from skills.learning_interests where tenant_id = ? and catalog_item_id = ?",
        UUID.class,
        tenant,
        item.getId());
  }

  private static Path newEvidenceFolder() {
    try {
      return Files.createTempDirectory("ayni-cleanup-evidence-test");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
