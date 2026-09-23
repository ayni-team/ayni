package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * {@link SkillsApi} against a real PostgreSQL.
 *
 * <p>The unit tests of {@code SkillsService} mock the repository, so they cannot tell whether a
 * query runs at all. This one exists because one did not: a derived query that promised
 * identifiers and returned entities passed every mocked test and failed on the first real call.
 *
 * <p>Configured like {@code OfferApprovedCourseAcceptanceTest} so both share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkillsApiDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";
  private static final BigDecimal GRADE = new BigDecimal("16.00");
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");
  private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");

  private final UUID tutor = UUID.randomUUID();

  @Autowired private SkillsApi skills;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @MockitoBean private IdentityApi identity;

  private static String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private CatalogItem course(String tenantId) {
    UUID category =
        categories.save(new Category(UUID.randomUUID(), "Category " + unique(), (short) 0)).getId();
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            tenantId,
            category,
            "Course " + unique(),
            null,
            "C" + unique(),
            NOW));
  }

  private OfferedSkill enable(String tenantId, UUID tutorId, CatalogItem item) {
    return offeredSkills.save(
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(), tenantId, tutorId, item.getId(), GRADE, THRESHOLD, NOW));
  }

  private static <T> T asUpc(Supplier<T> work) {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = work.get());
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  @Test
  void enabledSkillsOfReturnsOnlyTheTutorsEnabledItemsOfTheCurrentUniversity() {
    CatalogItem enabled = course(UPC);
    CatalogItem withdrawn = course(UPC);
    OfferedSkill toWithdraw = enable(UPC, tutor, withdrawn);
    toWithdraw.withdraw(NOW);
    offeredSkills.save(toWithdraw);
    enable(UPC, tutor, enabled);
    enable(UPC, UUID.randomUUID(), course(UPC));

    List<UUID> ids = asUpc(() -> skills.enabledSkillsOf(tutor));

    assertThat(ids).containsExactly(enabled.getId());
  }

  @Test
  void isTutorEnabledForAnswersFromTheDatabase() {
    CatalogItem item = course(UPC);
    enable(UPC, tutor, item);

    assertThat(asUpc(() -> skills.isTutorEnabledFor(tutor, item.getId()))).isTrue();
    assertThat(asUpc(() -> skills.isTutorEnabledFor(tutor, course(UPC).getId()))).isFalse();
  }

  @Test
  void requireItemRefusesACourseOfAnotherUniversity() {
    CatalogItem foreign = course(UTEC);

    assertThatThrownBy(() -> asUpc(() -> skills.requireItem(foreign.getId())))
        .isInstanceOf(NoSuchElementException.class);
  }
}
