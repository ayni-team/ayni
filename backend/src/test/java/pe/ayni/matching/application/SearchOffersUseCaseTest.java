package pe.ayni.matching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pe.ayni.matching.domain.MatchingFixtures.DATABASES;
import static pe.ayni.matching.domain.MatchingFixtures.UPC;
import static pe.ayni.matching.domain.MatchingFixtures.newTutor;
import static pe.ayni.matching.domain.MatchingFixtures.rated;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.MatchingRuleViolation;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** US01's search without Spring: what it asks the projection, and what it makes of the answer. */
class SearchOffersUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
  private static final Instant FROM = Instant.parse("2026-09-30T19:00:00Z");
  private static final Instant TO = Instant.parse("2026-09-30T23:00:00Z");

  private final UUID student = UUID.randomUUID();
  private final AvailableOfferRepository offers = mock(AvailableOfferRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final SearchOffersUseCase search =
      new SearchOffersUseCase(offers, identity, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void aUniversityInLima() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", new BigDecimal("13.00"), true));
  }

  private OfferSearch searchAs(int page, int size) {
    AtomicReference<OfferSearch> found = new AtomicReference<>();
    TenantContext.runAs(
        UPC, () -> found.set(search.execute(student, DATABASES, FROM, TO, page, size)));
    return found.get();
  }

  private void inTheWindow(List<AvailableOffer> within, long total) {
    when(offers.findWithin(
            eq(UPC), eq(DATABASES), eq(student), eq(NOW), eq(FROM), eq(TO), any()))
        .thenAnswer(
            call -> new PageImpl<>(within, call.getArgument(6, PageRequest.class), total));
  }

  @Test
  @DisplayName("every tutor free in the window comes back as an exact match, in the university's zone")
  void everyTutorInTheWindow() {

    AvailableOffer best = rated("Bruno Salas", FROM, "4.80");
    AvailableOffer newcomer = newTutor("Carla Ruiz", FROM);
    inTheWindow(List.of(best, newcomer), 2);

    OfferSearch found = searchAs(0, 20);

    assertThat(found.exactMatch()).isTrue();
    assertThat(found.timezone()).isEqualTo("America/Lima");
    assertThat(found.offers())
        .extracting(FoundOffer::tutorName, FoundOffer::newTutor)
        .containsExactly(
            tuple("Bruno Salas", false),
            tuple("Carla Ruiz", true));
    assertThat(found.offers().get(1).averageStars()).isNull();
    assertThat(found.offers().getFirst().endsAt()).isEqualTo(FROM.plus(Duration.ofHours(1)));
    assertThat(found.totalElements()).isEqualTo(2);
    assertThat(found.totalPages()).isEqualTo(1);
    verify(offers, never()).findClosestAfter(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("the projection is asked from the clock's now, so started hours never come back")
  void theClockDecidesWhatHasStarted() {

    inTheWindow(List.of(rated("Bruno Salas", FROM, "4.80")), 1);

    searchAs(0, 20);

    verify(offers)
        .findWithin(UPC, DATABASES, student, NOW, FROM, TO, PageRequest.of(0, 20));
  }

  @Test
  @DisplayName("an empty window answers with the closest hours outside it, not with nothing")
  void anEmptyWindowFallsBackToTheClosest() {

    inTheWindow(List.of(), 0);
    AvailableOffer before = rated("Ana Torres", FROM.minus(Duration.ofHours(1)), "4.00");
    AvailableOffer after = newTutor("Carla Ruiz", TO.plus(Duration.ofHours(2)));
    when(offers.findClosestBefore(UPC, DATABASES, student, NOW, FROM, Limit.of(5)))
        .thenReturn(List.of(before));
    when(offers.findClosestAfter(UPC, DATABASES, student, NOW, TO, Limit.of(5)))
        .thenReturn(List.of(after));

    OfferSearch found = searchAs(0, 5);

    assertThat(found.exactMatch()).isFalse();
    assertThat(found.offers())
        .extracting(FoundOffer::blockId)
        .containsExactly(before.getBlockId(), after.getBlockId());
    assertThat(found.totalElements()).isEqualTo(2);
    assertThat(found.totalPages()).isEqualTo(1);
    assertThat(found.timezone()).isEqualTo("America/Lima");
  }

  @Test
  @DisplayName("the closest hours are a single page: a later page of them is empty")
  void theFallbackIsASinglePage() {

    inTheWindow(List.of(), 0);
    when(offers.findClosestBefore(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    when(offers.findClosestAfter(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(rated("Ana Torres", TO, "4.00")));

    OfferSearch found = searchAs(1, 5);

    assertThat(found.exactMatch()).isFalse();
    assertThat(found.offers()).isEmpty();
    assertThat(found.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("nothing anywhere is an empty answer that is not an exact match")
  void nothingAnywhere() {

    inTheWindow(List.of(), 0);
    when(offers.findClosestBefore(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    when(offers.findClosestAfter(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

    OfferSearch found = searchAs(0, 20);

    assertThat(found.exactMatch()).isFalse();
    assertThat(found.offers()).isEmpty();
    assertThat(found.totalPages()).isZero();
  }

  @Test
  @DisplayName("a window that does not end after it starts is refused before anything is read")
  void aBackwardsWindowIsRefused() {

    TenantContext.runAs(
        UPC,
        () ->
            assertThatThrownBy(() -> search.execute(student, DATABASES, TO, FROM, 0, 20))
                .isInstanceOf(MatchingRuleViolation.class)
                .hasMessage("from must be before to"));

    verifyNoInteractions(offers, identity);
  }

  @Test
  @DisplayName("a page out of range is refused")
  void aPageOutOfRangeIsRefused() {

    TenantContext.runAs(
        UPC,
        () -> {
          assertThatThrownBy(() -> search.execute(student, DATABASES, FROM, TO, -1, 20))
              .isInstanceOf(MatchingRuleViolation.class);
          assertThatThrownBy(() -> search.execute(student, DATABASES, FROM, TO, 0, 0))
              .isInstanceOf(MatchingRuleViolation.class);
          assertThatThrownBy(
                  () ->
                      search.execute(
                          student, DATABASES, FROM, TO, 0, SearchOffersUseCase.MAX_PAGE_SIZE + 1))
              .isInstanceOf(MatchingRuleViolation.class);
        });

    verifyNoInteractions(offers);
  }
}
