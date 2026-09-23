package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** What the rest of the platform is allowed to know about a tutor's skills. */
class SkillsServiceTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID CATALOG_ITEM_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");

  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final LearningInterestRepository learningInterests = mock(LearningInterestRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  // OfferApprovedCourseUseCase is built for real: mocking a concrete class needs bytecode
  // instrumentation some JDKs refuse to perform, and this is the more faithful test anyway.
  private final OfferApprovedCourseUseCase offerApprovedCourse =
      new OfferApprovedCourseUseCase(
          catalogItems,
          offeredSkills,
          identity,
          mock(ApplicationEventPublisher.class),
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final DeclareInterestsUseCase declareInterests =
      new DeclareInterestsUseCase(
          learningInterests, offerApprovedCourse, Clock.fixed(NOW, ZoneOffset.UTC));
  private final SkillsService service = new SkillsService(offeredSkills, catalogItems, declareInterests);

  private <T> T asUpc(java.util.function.Supplier<T> work) {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = work.get());
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  @Test
  @DisplayName("a tutor enabled for the item answers true")
  void aTutorEnabledForTheItemAnswersTrue() {
    when(offeredSkills.existsByTenantIdAndTutorIdAndCatalogItemIdAndStatus(
            UPC, TUTOR, CATALOG_ITEM_ID, OfferedSkillStatus.ENABLED))
        .thenReturn(true);

    assertThat(asUpc(() -> service.isTutorEnabledFor(TUTOR, CATALOG_ITEM_ID))).isTrue();
  }

  @Test
  @DisplayName("enabledSkillsOf reads only this tutor's enabled items")
  void enabledSkillsOfReadsOnlyThisTutorsEnabledItems() {
    when(offeredSkills.findCatalogItemIdByTenantIdAndTutorIdAndStatus(
            UPC, TUTOR, OfferedSkillStatus.ENABLED))
        .thenReturn(List.of(CATALOG_ITEM_ID));

    assertThat(asUpc(() -> service.enabledSkillsOf(TUTOR))).containsExactly(CATALOG_ITEM_ID);
  }

  @Test
  @DisplayName("requireItem returns the item as the other modules see it")
  void requireItemReturnsTheItemAsTheOtherModulesSeeIt() {
    CatalogItem item =
        new CatalogItem(
            CATALOG_ITEM_ID,
            CatalogScope.UNIVERSITY,
            UPC,
            UUID.randomUUID(),
            "Fundamentos",
            null,
            "1ASI0657",
            NOW);
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC)).thenReturn(Optional.of(item));

    CatalogItemView view = asUpc(() -> service.requireItem(CATALOG_ITEM_ID));

    assertThat(view)
        .isEqualTo(
            new CatalogItemView(CATALOG_ITEM_ID, CatalogScope.UNIVERSITY, "Fundamentos", "1ASI0657"));
  }

  @Test
  @DisplayName("requireItem refuses an item invisible to the current tenant")
  void requireItemRefusesAnItemInvisibleToTheCurrentTenant() {
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> asUpc(() -> service.requireItem(CATALOG_ITEM_ID)))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("declareLearningInterests registers a new interest")
  void declareLearningInterestsRegistersANewInterest() {
    when(learningInterests.findByTenantIdAndStudentIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.empty());
    when(learningInterests.save(any())).thenAnswer(call -> call.getArgument(0));

    TenantContext.runAs(UPC, () -> service.declareLearningInterests(TUTOR, List.of(CATALOG_ITEM_ID)));

    verify(learningInterests).save(any());
  }

  @Test
  @DisplayName("declareTeachingInterests reports the items that ended up enabled")
  void declareTeachingInterestsReportsTheItemsThatEndedUpEnabled() {
    CatalogItem course =
        new CatalogItem(
            CATALOG_ITEM_ID, CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Fundamentos", null,
            "1ASI0657", NOW);
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC)).thenReturn(Optional.of(course));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.empty());
    when(offeredSkills.save(any())).thenAnswer(call -> call.getArgument(0));
    when(identity.approvedCourses(TUTOR))
        .thenReturn(List.of(new ApprovedCourseView("1ASI0657", "Fundamentos", new BigDecimal("15.00"), "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(new TenantView(UPC, "UPC", "America/Lima", THRESHOLD, true));

    List<UUID> result =
        asUpc(() -> service.declareTeachingInterests(TUTOR, List.of(CATALOG_ITEM_ID)));

    assertThat(result).containsExactly(CATALOG_ITEM_ID);
  }
}
