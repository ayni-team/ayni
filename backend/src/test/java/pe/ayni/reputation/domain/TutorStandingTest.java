package pe.ayni.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.reputation.domain.model.TutorStanding;

/** The rules a tutor's standing in one skill holds by itself, without Spring. */
class TutorStandingTest {

    private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");

    private static TutorStanding withRatings(int ratingsCount, String average) {
        return new TutorStanding(
                "UPC",
                UUID.randomUUID(),
                UUID.randomUUID(),
                ratingsCount,
                ratingsCount,
                average == null ? null : new BigDecimal(average),
                NOW);
    }

    @Test
    @DisplayName("below three ratings the tutor is new and no average is shown")
    void belowThreeRatingsTheTutorIsNew() {
        TutorStanding standing = withRatings(2, "4.50");

        assertThat(standing.isNew()).isTrue();
        assertThat(standing.visibleAverageStars()).isNull();
    }

    @Test
    @DisplayName("from the third rating the average is shown")
    void fromTheThirdRatingTheAverageIsShown() {
        TutorStanding standing = withRatings(3, "4.33");

        assertThat(standing.isNew()).isFalse();
        assertThat(standing.visibleAverageStars()).isEqualByComparingTo("4.33");
    }

    @Test
    @DisplayName("a first completed session opens the standing as new and counts it")
    void aFirstCompletedSessionCountsAsNew() {
        TutorStanding standing = TutorStanding.initial("UPC", UUID.randomUUID(), UUID.randomUUID(), NOW);

        standing.recordCompletedSession(NOW.plusSeconds(60));

        assertThat(standing.sessionsTaught()).isEqualTo(1);
        assertThat(standing.isNew()).isTrue();
        assertThat(standing.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }
}
