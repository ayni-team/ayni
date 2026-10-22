package pe.ayni.skills.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A catalogue to look at while nothing else fills it.
 *
 * <p>No story in this cycle creates a category or a global tool, and university courses will come
 * from each institution's academic system, which the platform does not integrate with yet. Until
 * then US13 would have nothing to offer, so under the {@code dev} profile the module fills a small
 * catalogue: a few global tools and the courses of one university.
 *
 * <p>It lives here and not in a migration on purpose. A migration runs in every environment and
 * can never be taken back; demonstration data belongs to the developer's machine only, the same
 * way {@code DemoWalletData} does for wallet.
 *
 * <p>The three global categories are reference data and come from a migration in every
 * environment; this only makes sure they are there and adds the category of university courses.
 *
 * <p>It runs once. A second start finds the first global tool already there and leaves everything
 * alone; it looks for the tool and not for a category, because the categories exist from the
 * migration before anything here has run. The identifiers are fixed so that requests saved in
 * Swagger or in the frontend keep working.
 */
@Component
@Profile("dev")
class DemoSkillsData implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSkillsData.class);

  private static final String TENANT = "UPC";

  private static final UUID TECHNOLOGY = UUID.fromString("a0000000-0000-4000-8000-000000000001");
  private static final UUID LANGUAGES = UUID.fromString("a0000000-0000-4000-8000-000000000002");
  private static final UUID DESIGN = UUID.fromString("a0000000-0000-4000-8000-000000000003");
  private static final UUID UNIVERSITY_COURSES =
      UUID.fromString("a0000000-0000-4000-8000-000000000004");

  /** The first global tool: once it is there, the demo catalogue has been filled. */
  private static final UUID PYTHON = UUID.fromString("b0000000-0000-4000-8000-000000000001");

  private final CategoryRepository categories;
  private final CatalogItemRepository catalogItems;
  private final Clock clock;

  DemoSkillsData(CategoryRepository categories, CatalogItemRepository catalogItems, Clock clock) {
    this.categories = categories;
    this.catalogItems = catalogItems;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Row level security (TS01): its rows are written, and looked for, as the demo university's.
    TenantContext.runAs(TENANT, this::fill);
  }

  private void fill() {
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
    UUID universityCourses =
        categories.findByName("University courses").map(Category::getId).orElseThrow();

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

    // University courses, each with the code its academic system reports on an approved course.
    course("b0000000-0000-4000-8000-000000000101", universityCourses, "1ASI0657",
        "Software Architecture Fundamentals", "Architectural styles, patterns and views.", now);
    course("b0000000-0000-4000-8000-000000000102", universityCourses, "1ASI0616",
        "Databases I", "Relational modelling, SQL and normalisation.", now);
    course("b0000000-0000-4000-8000-000000000103", universityCourses, "1MAT0101",
        "Calculus I", "Limits, derivatives and integrals of one variable.", now);
    course("b0000000-0000-4000-8000-000000000104", universityCourses, "1ASI0625",
        "Backend Development", "Designing and building server side services.", now);

    log.info("Demo catalogue ready: 4 global tools and 4 courses of {}", TENANT);
  }

  private void tool(String id, UUID category, String name, String description, Instant now) {
    catalogItems.save(
        new CatalogItem(
            UUID.fromString(id), CatalogScope.GLOBAL, null, category, name, description, null,
            now));
  }

  private void course(
      String id, UUID category, String courseCode, String name, String description, Instant now) {
    catalogItems.save(
        new CatalogItem(
            UUID.fromString(id),
            CatalogScope.UNIVERSITY,
            TENANT,
            category,
            name,
            description,
            courseCode,
            now));
  }
}
