package pe.ayni.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.reputation.domain.model.NotAParticipant;
import pe.ayni.reputation.domain.model.Rating;
import pe.ayni.reputation.domain.model.RatingWindow;

/** Which direction each participant rates, and reads back, without Spring. */
class RatingWindowTest {

    private final UUID tutor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final RatingWindow window =
            new RatingWindow(
                    "UPC",
                    UUID.randomUUID(),
                    tutor,
                    student,
                    UUID.randomUUID(),
                    Instant.parse("2026-10-20T15:00:00Z"));

    @Test
    @DisplayName("the student rates the tutor")
    void theStudentRatesTheTutor() {
        assertThat(window.directionRatedBy(student)).isEqualTo(Rating.Direction.STUDENT_TO_TUTOR);
    }

    @Test
    @DisplayName("the tutor assesses the student")
    void theTutorAssessesTheStudent() {
        assertThat(window.directionRatedBy(tutor)).isEqualTo(Rating.Direction.TUTOR_TO_STUDENT);
    }

    @Test
    @DisplayName("somebody else has no direction in the session")
    void somebodyElseHasNoDirection() {
        assertThatThrownBy(() -> window.directionRatedBy(UUID.randomUUID()))
                .isInstanceOf(NotAParticipant.class);
    }
}
