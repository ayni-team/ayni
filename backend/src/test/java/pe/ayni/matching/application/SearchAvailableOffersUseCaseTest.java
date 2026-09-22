package pe.ayni.matching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** A student comparing tutors for the same hour, and a search that finds nothing exact. */
class SearchAvailableOffersUseCaseTest {

    private static final String UPC = "UPC";
    private static final UUID COURSE = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-09-25T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-26T00:00:00Z");

    private final AvailableOfferRepository offers = mock(AvailableOfferRepository.class);
    private final SearchAvailableOffersUseCase useCase = new SearchAvailableOffersUseCase(offers);

    private AvailableOffer offer(UUID tutorId, Instant startsAt, BigDecimal rating, boolean isNew) {
        return new AvailableOffer(
                UUID.randomUUID(),
                UPC,
                tutorId,
                COURSE,
                UUID.randomUUID(),
                startsAt,
                startsAt.plusSeconds(3600),
                rating,
                isNew,
                startsAt);
    }

    private SearchAvailableOffersUseCase.Result search() {
        SearchAvailableOffersUseCase.Result[] result = new SearchAvailableOffersUseCase.Result[1];
        TenantContext.runAs(UPC, () -> result[0] = useCase.execute(COURSE, FROM, TO));
        return result[0];
    }

    @Test
    @DisplayName("returns every tutor free at the same hour, marked as an exact match")
    void returnsMultipleTutorsAtTheSameHour() {

        Instant sameHour = Instant.parse("2026-09-25T14:00:00Z");
        AvailableOffer topRated = offer(UUID.randomUUID(), sameHour, new BigDecimal("4.80"), false);
        AvailableOffer newTutor = offer(UUID.randomUUID(), sameHour, null, true);

        // The repository query already orders by rating; the use case must not reshuffle it.
        when(offers.findByTenantIdAndCourseIdAndStartsAtBetweenOrderByStartsAtAscTutorRatingDesc(
                UPC, COURSE, FROM, TO))
                .thenReturn(List.of(topRated, newTutor));

        SearchAvailableOffersUseCase.Result result = search();

        assertThat(result.exactMatch()).isTrue();
        assertThat(result.offers()).hasSize(2);
        assertThat(result.offers().get(0).tutorId()).isEqualTo(topRated.getTutorId());
        assertThat(result.offers().get(0).tutorRating()).isEqualByComparingTo("4.80");
        assertThat(result.offers().get(1).tutorId()).isEqualTo(newTutor.getTutorId());
        assertThat(result.offers().get(1).newTutor()).isTrue();
    }

    @Test
    @DisplayName("keeps the block's start and end exactly as stored, in UTC, with no conversion")
    void preservesUtcInstantsWithoutConversion() {

        Instant startsAt = Instant.parse("2026-09-25T23:30:00Z");
        AvailableOffer lateBlock = offer(UUID.randomUUID(), startsAt, null, false);

        when(offers.findByTenantIdAndCourseIdAndStartsAtBetweenOrderByStartsAtAscTutorRatingDesc(
                UPC, COURSE, FROM, TO))
                .thenReturn(List.of(lateBlock));

        SearchAvailableOffersUseCase.Result result = search();

        assertThat(result.offers().get(0).startsAt()).isEqualTo(startsAt);
        assertThat(result.offers().get(0).endsAt()).isEqualTo(startsAt.plusSeconds(3600));
    }

    @Test
    @DisplayName("falls back to the closest blocks outside the range when it is empty")
    void fallsBackToNearestBlocksWhenRangeIsEmpty() {

        when(offers.findByTenantIdAndCourseIdAndStartsAtBetweenOrderByStartsAtAscTutorRatingDesc(
                UPC, COURSE, FROM, TO))
                .thenReturn(List.of());

        Instant justAfter = TO.plusSeconds(3600); // 1h past the range
        Instant farAfter = TO.plusSeconds(7200); // 2h past the range
        Instant justBefore = FROM.minusSeconds(1800); // 30min before the range: the closest of all

        when(offers.findTop10ByTenantIdAndCourseIdAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                UPC, COURSE, TO))
                .thenReturn(
                        List.of(
                                offer(UUID.randomUUID(), justAfter, null, false),
                                offer(UUID.randomUUID(), farAfter, null, false)));
        when(offers.findTop10ByTenantIdAndCourseIdAndStartsAtLessThanOrderByStartsAtDesc(
                UPC, COURSE, FROM))
                .thenReturn(List.of(offer(UUID.randomUUID(), justBefore, null, false)));

        SearchAvailableOffersUseCase.Result result = search();

        assertThat(result.exactMatch()).isFalse();
        assertThat(result.offers()).extracting(AvailableOfferView::startsAt)
                .containsExactly(justBefore, justAfter, farAfter);
    }
}