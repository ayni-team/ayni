package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** US18, scenario 1: the tutor sees every skill with where it stands. */
class TutorSkillsQueryTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final TutorSkillsQuery query = new TutorSkillsQuery(offeredSkills, catalogItems);

  private static CatalogItem course(String name, String courseCode) {
    return new CatalogItem(
        UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), name, null, courseCode, NOW);
  }

  private static OfferedSkill enabledFor(CatalogItem item) {
    return OfferedSkill.enableByAcademicRecord(
        UUID.randomUUID(), UPC, TUTOR, item.getId(), new BigDecimal("15.00"), new BigDecimal("13.00"), NOW);
  }

  @Test
  @DisplayName("lists every skill, withdrawn ones included, sorted by the name of the item")
  void listsEverySkillSortedByName() {
    CatalogItem databases = course("Databases", "1ASI0616");
    CatalogItem architecture = course("Architecture", "1ASI0657");
    OfferedSkill enabled = enabledFor(databases);
    OfferedSkill withdrawn = enabledFor(architecture);
    withdrawn.withdraw(NOW.plusSeconds(60));
    when(offeredSkills.findByTenantIdAndTutorId(UPC, TUTOR)).thenReturn(List.of(enabled, withdrawn));
    when(catalogItems.findAllById(List.of(databases.getId(), architecture.getId())))
        .thenReturn(List.of(databases, architecture));

    List<TutorSkillsQuery.TutorSkill> result = runAsUpc();

    assertThat(result)
        .extracting(tutorSkill -> tutorSkill.item().getName())
        .containsExactly("Architecture", "Databases");
    assertThat(result.get(0).skill()).isSameAs(withdrawn);
  }

  @Test
  @DisplayName("a tutor with no skills gets an empty list")
  void aTutorWithNoSkillsGetsAnEmptyList() {
    when(offeredSkills.findByTenantIdAndTutorId(UPC, TUTOR)).thenReturn(List.of());
    when(catalogItems.findAllById(List.of())).thenReturn(List.of());

    assertThat(runAsUpc()).isEmpty();
  }

  private List<TutorSkillsQuery.TutorSkill> runAsUpc() {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = query.of(TUTOR));
    @SuppressWarnings("unchecked")
    List<TutorSkillsQuery.TutorSkill> typed = (List<TutorSkillsQuery.TutorSkill>) result[0];
    return typed;
  }
}
