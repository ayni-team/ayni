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
 * <p>It runs once. A second start finds the categories already there and leaves them alone. The
 * identifiers are fixed so that requests saved in Swagger or in the frontend keep working.
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
    if (categories.existsById(TECHNOLOGY)) {
      return;
    }

    categories.save(new Category(TECHNOLOGY, "Technology", (short) 1));
    categories.save(new Category(LANGUAGES, "Languages", (short) 2));
    categories.save(new Category(DESIGN, "Design", (short) 3));
    categories.save(new Category(UNIVERSITY_COURSES, "University courses", (short) 4));

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
    course("b0000000-0000-4000-8000-000000000101", "1ASI0657",
        "Software Architecture Fundamentals", "Architectural styles, patterns and views.", now);
    course("b0000000-0000-4000-8000-000000000102", "1ASI0616",
        "Databases I", "Relational modelling, SQL and normalisation.", now);
    course("b0000000-0000-4000-8000-000000000103", "1MAT0101",
        "Calculus I", "Limits, derivatives and integrals of one variable.", now);
    course("b0000000-0000-4000-8000-000000000104", "1ASI0625",
        "Backend Development", "Designing and building server side services.", now);

    log.info("Demo catalogue ready: 4 global tools and 4 courses of {}", TENANT);
  }

  private void tool(String id, UUID category, String name, String description, Instant now) {
    catalogItems.save(
        new CatalogItem(
            UUID.fromString(id), CatalogScope.GLOBAL, null, category, name, description, null,
            now));
  }

  private void course(String id, String courseCode, String name, String description, Instant now) {
    catalogItems.save(
        new CatalogItem(
            UUID.fromString(id),
            CatalogScope.UNIVERSITY,
            TENANT,
            UNIVERSITY_COURSES,
            name,
            description,
            courseCode,
            now));
  }
}
