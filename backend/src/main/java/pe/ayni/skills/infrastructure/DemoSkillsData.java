package pe.ayni.skills.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.ayni.identity.AcademicCourseView;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.CompleteOnboardingUseCase;
import pe.ayni.skills.application.SyncAcademicCatalogUseCase;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;

/**
 * A catalogue to look at, and the tutors of the demonstration enabled on it.
 *
 * <p>Three things, under the {@code dev} profile only:
 *
 * <ul>
 *   <li>A few global tools, the same for every university, and the four categories. The three
 *       global categories are reference data and come from a migration in every environment; this
 *       only makes sure they are there and adds the category of university courses.
 *   <li>The curriculum of UPC and of USIL, synchronised from their academic documents with the
 *       use case of {@code POST /api/v1/coordinator/academic-catalog/sync} (ADR 0006). Nothing about
 *       courses is written in this class.
 *   <li>The last step of the onboarding of the two tutors {@code DemoIdentityData} leaves at the
 *       interests, Bruno of UPC and Mateo of USIL, with the use case of {@code POST
 *       /api/v1/me/onboarding/interests}: they offer every course they approved, and the ones
 *       whose grade reaches the university's threshold are enabled, the rest skipped.
 * </ul>
 *
 * <p>It lives here and not in a migration on purpose. A migration runs in every environment and
 * can never be taken back; demonstration data belongs to the developer's machine only, the same
 * way {@code DemoWalletData} does for wallet.
 *
 * <p>It runs after {@code DemoIdentityData}, and each part once: the tools when the first one is
 * missing, a curriculum when the university has none of its courses yet (so restarting does not
 * bring back a course a coordinator retired), and a tutor's onboarding when they offer nothing yet.
 * The identifiers of the tools are fixed so that requests saved in Swagger or in the frontend keep
 * working.
 */
@Component
@Profile("dev")
@Order(2)
class DemoSkillsData implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSkillsData.class);

  private static final List<String> UNIVERSITIES = List.of("UPC", "USIL");

  /** Bruno of UPC and Mateo of USIL, the tutors of {@code DemoIdentityData}. */
  private record Tutor(String tenant, UUID id) {}

  private static final List<Tutor> TUTORS =
      List.of(
          new Tutor("UPC", UUID.fromString("22222222-2222-4222-8222-222222222222")),
          new Tutor("USIL", UUID.fromString("77777777-7777-4777-8777-777777777777")));

  private static final UUID TECHNOLOGY = UUID.fromString("a0000000-0000-4000-8000-000000000001");
  private static final UUID LANGUAGES = UUID.fromString("a0000000-0000-4000-8000-000000000002");
  private static final UUID DESIGN = UUID.fromString("a0000000-0000-4000-8000-000000000003");
  private static final UUID UNIVERSITY_COURSES =
      UUID.fromString("a0000000-0000-4000-8000-000000000004");

  /** The first global tool: once it is there, the tools have been written. */
  private static final UUID PYTHON = UUID.fromString("b0000000-0000-4000-8000-000000000001");

  private final CategoryRepository categories;
  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final SyncAcademicCatalogUseCase syncAcademicCatalog;
  private final CompleteOnboardingUseCase completeOnboarding;
  private final IdentityApi identity;
  private final TransactionTemplate transactions;
  private final Clock clock;

  DemoSkillsData(
      CategoryRepository categories,
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      SyncAcademicCatalogUseCase syncAcademicCatalog,
      CompleteOnboardingUseCase completeOnboarding,
      IdentityApi identity,
      TransactionTemplate transactions,
      Clock clock) {
    this.categories = categories;
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.syncAcademicCatalog = syncAcademicCatalog;
    this.completeOnboarding = completeOnboarding;
    this.identity = identity;
    this.transactions = transactions;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    // Row level security (TS01): the global rows are read and written under one university, as
    // they always were; each curriculum and each tutor under their own.
    TenantContext.runAs(UNIVERSITIES.get(0), () -> transactions.executeWithoutResult(done -> tools()));
    for (String university : UNIVERSITIES) {
      TenantContext.runAs(university, () -> curriculumOf(university));
    }
    for (Tutor tutor : TUTORS) {
      TenantContext.runAs(tutor.tenant(), () -> onboard(tutor));
    }
  }

  private void tools() {
    if (catalogItems.existsById(PYTHON)) {
      return;
    }

    // Each one unless it exists, by its identifier or by its name: the global three come from the
    // migration, and the category of courses may already have been created by loading a
    // curriculum (US51), with an identifier of its own.
    categories.createIfMissing(TECHNOLOGY, "Technology", (short) 1);
    categories.createIfMissing(LANGUAGES, "Languages", (short) 2);
    categories.createIfMissing(DESIGN, "Design", (short) 3);
    categories.createIfMissing(UNIVERSITY_COURSES, "University courses", (short) 4);

    Instant now = clock.instant();

    // Global tools: the same for every university, no course code.
    tool("b0000000-0000-4000-8000-000000000001", TECHNOLOGY, "Python",
        "Python programming, from basic syntax to data libraries.", now);
    tool("b0000000-0000-4000-8000-000000000002", TECHNOLOGY, "Git and version control",
        "Working with Git: branches, pull requests and resolving conflicts.", now);
    tool("b0000000-0000-4000-8000-000000000003", LANGUAGES, "Conversational English",
        "Conversation practice for intermediate learners.", now);
    tool("b0000000-0000-4000-8000-000000000004", DESIGN, "Figma",
        "Interface design and prototyping in Figma.", now);

    log.info("Demo catalogue ready: 4 global tools and the categories");
  }

  private void curriculumOf(String university) {
    List<String> codes;
    try {
      codes = identity.academicCurriculum(university).stream().map(AcademicCourseView::code).toList();
    } catch (NoSuchElementException noDocument) {
      log.warn("No demo curriculum for {}: it has no academic document", university);
      return;
    }
    if (!catalogItems.findByTenantIdAndCourseCodeIn(university, codes).isEmpty()) {
      return;
    }
    int loaded = syncAcademicCatalog.withoutCoordinator().size();
    log.info("Demo curriculum of {} synchronised: {} courses", university, loaded);
  }

  private void onboard(Tutor tutor) {
    if (!offeredSkills.findByTenantIdAndTutorId(tutor.tenant(), tutor.id()).isEmpty()) {
      return;
    }
    List<String> approved;
    try {
      identity.requireUser(tutor.id());
      approved = identity.approvedCourses(tutor.id()).stream().map(ApprovedCourseView::courseCode).toList();
    } catch (NoSuchElementException absent) {
      log.warn("No demo tutor {} in {}: the person does not exist", tutor.id(), tutor.tenant());
      return;
    }
    List<UUID> teaching =
        catalogItems
            .findByTenantIdAndCourseCodeInAndStatus(tutor.tenant(), approved, CatalogItemStatus.ACTIVE)
            .stream()
            .map(CatalogItem::getId)
            .toList();
    int enabled = completeOnboarding.execute(tutor.id(), List.of(), teaching).teaching().size();
    log.info(
        "Demo tutor {} of {} onboarded: {} of {} approved courses enabled",
        tutor.id(),
        tutor.tenant(),
        enabled,
        teaching.size());
  }

  private void tool(String id, UUID category, String name, String description, Instant now) {
    catalogItems.save(
        new CatalogItem(
            UUID.fromString(id), CatalogScope.GLOBAL, null, category, name, description, null,
            now));
  }
}
