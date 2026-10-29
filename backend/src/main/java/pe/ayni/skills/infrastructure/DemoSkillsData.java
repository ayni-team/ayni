package pe.ayni.skills.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.demo.DemoCast.Person;
import pe.ayni.shared.demo.DemoCast.Step;
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
 *   <li>The last step of the onboarding of every person of {@link DemoCast} that ends it, with the
 *       use case of {@code POST /api/v1/me/onboarding/interests}. Bruno of UPC and Mateo of USIL
 *       offer every course they approved, and the ones whose grade reaches the university's
 *       threshold are enabled, the rest skipped. The other three tutors of each university offer
 *       five courses each, chosen among those, so that a tutor is not one more copy of Bruno and
 *       some courses are left with nobody to teach them: a search for those finds nobody, which is
 *       the demand the coordinator's indicators count. The students who do not teach declare what
 *       they want to learn, a few courses of {@link #WANTED} they have not approved.
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

  /**
   * The courses each tutor offers, by code; every approved one for the two the demonstration
   * started with. Every tutor is enabled only where their grade reaches the threshold.
   */
  private static final Map<UUID, List<String>> TEACHES =
      Map.of(
          DemoCast.BRUNO.id(), List.of(),
          DemoCast.MATEO_CARDENAS.id(), List.of("CC200", "AD99", "CC201", "MA262", "CA59"),
          DemoCast.DANIELA_SALAS.id(), List.of("CC182", "MA462", "SI385", "MA642", "SI729"),
          DemoCast.ISABELLA_TAPIA.id(), List.of("SI704", "CC200", "HU612", "HU625", "MA420"),
          DemoCast.MATEO.id(), List.of(),
          DemoCast.JOAQUIN_MEDINA.id(),
              List.of("USIL001", "USIL007", "USIL013", "USIL014", "USIL015"),
          DemoCast.DANIELA_TAPIA.id(),
              List.of("USIL020", "USIL021", "USIL001", "USIL002", "USIL003"),
          DemoCast.PIERO_DELGADO.id(),
              List.of("USIL016", "USIL019", "USIL022", "USIL001", "USIL002"));

  /** The tutors who teach every course they approved well enough. */
  private static final Set<UUID> TEACH_EVERYTHING = Set.of(DemoCast.BRUNO.id(), DemoCast.MATEO.id());

  /**
   * What the students want to learn: the first three courses of each list they have not approved.
   * The first ones of each university have no tutor among the demonstration's.
   */
  private static final Map<String, List<String>> WANTED =
      Map.of(
          DemoCast.UPC, List.of("SI643", "SI705", "SI427", "SI400", "CC182", "MA262"),
          DemoCast.USIL, List.of("USIL026", "USIL034", "USIL033", "USIL001", "USIL013", "USIL020"));

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
  private final LearningInterestRepository learningInterests;
  private final SyncAcademicCatalogUseCase syncAcademicCatalog;
  private final CompleteOnboardingUseCase completeOnboarding;
  private final IdentityApi identity;
  private final TransactionTemplate transactions;
  private final Clock clock;

  DemoSkillsData(
      CategoryRepository categories,
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      LearningInterestRepository learningInterests,
      SyncAcademicCatalogUseCase syncAcademicCatalog,
      CompleteOnboardingUseCase completeOnboarding,
      IdentityApi identity,
      TransactionTemplate transactions,
      Clock clock) {
    this.categories = categories;
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.learningInterests = learningInterests;
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
    for (Person person : DemoCast.STUDENTS) {
      if (person.step() != Step.COMPLETED) {
        continue;
      }
      try {
        TenantContext.runAs(person.university(), () -> onboard(person));
      } catch (RuntimeException failure) {
        // Demonstration data must not keep the application from starting.
        log.error("Could not onboard the demo person {}", person.id(), failure);
      }
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

  private void onboard(Person person) {
    String tenant = person.university();
    if (!offeredSkills.findByTenantIdAndTutorId(tenant, person.id()).isEmpty()
        || !learningInterests.findByTenantIdAndStudentId(tenant, person.id()).isEmpty()) {
      return;
    }
    List<String> approved;
    try {
      identity.requireUser(person.id());
      approved =
          identity.approvedCourses(person.id()).stream().map(ApprovedCourseView::courseCode).toList();
    } catch (NoSuchElementException absent) {
      log.warn("No demo person {} in {}: the person does not exist", person.id(), tenant);
      return;
    }

    List<String> offers =
        TEACH_EVERYTHING.contains(person.id())
            ? approved
            : TEACHES.getOrDefault(person.id(), List.of());
    List<UUID> teaching = activeCourses(tenant, offers);
    List<UUID> learning =
        TEACHES.containsKey(person.id())
            ? List.of()
            : activeCourses(
                tenant,
                WANTED.get(tenant).stream().filter(code -> !approved.contains(code)).limit(3).toList());

    int enabled = completeOnboarding.execute(person.id(), learning, teaching).teaching().size();
    log.info(
        "Demo person {} of {} onboarded: {} courses enabled of {} offered, {} wanted",
        person.id(),
        tenant,
        enabled,
        teaching.size(),
        learning.size());
  }

  private List<UUID> activeCourses(String tenant, List<String> codes) {
    if (codes.isEmpty()) {
      return List.of();
    }
    return catalogItems
        .findByTenantIdAndCourseCodeInAndStatus(tenant, codes, CatalogItemStatus.ACTIVE)
        .stream()
        .map(CatalogItem::getId)
        .toList();
  }

  private void tool(String id, UUID category, String name, String description, Instant now) {
    catalogItems.save(
        new CatalogItem(
            UUID.fromString(id), CatalogScope.GLOBAL, null, category, name, description, null,
            now));
  }
}
