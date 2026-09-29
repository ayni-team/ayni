package pe.ayni.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.skills.SkillsApi;

/** US02's standing lookup without Spring: a tutor with history, a new one, and a stranger. */
class TutorStandingQueryTest {

    private final UUID tutor = UUID.randomUUID();
    private final UUID calculus = UUID.randomUUID();
    private final ReputationApi reputation = mock(ReputationApi.class);
    private final SkillsApi skills = mock(SkillsApi.class);
    private final TutorStandingQuery query = new TutorStandingQuery(reputation, skills);

    @Test
    @DisplayName("a tutor with history answers with their standing, without asking skills")
    void aTutorWithHistory() {
        TutorStandingView earned =
                new TutorStandingView(tutor, calculus, 8, 5, new BigDecimal("4.60"));
        when(reputation.standingOf(tutor, calculus)).thenReturn(Optional.of(earned));

        assertThat(query.execute(tutor, calculus)).isEqualTo(earned);
        verifyNoInteractions(skills);
    }

    @Test
    @DisplayName("a tutor enabled for the course who never taught it is new: nothing counted")
    void anEnabledTutorWithoutHistoryIsNew() {
        when(reputation.standingOf(tutor, calculus)).thenReturn(Optional.empty());
        when(skills.isTutorEnabledFor(tutor, calculus)).thenReturn(true);

        TutorStandingView standing = query.execute(tutor, calculus);

        assertThat(standing).isEqualTo(new TutorStandingView(tutor, calculus, 0, 0, null));
    }

    @Test
    @DisplayName("a tutor not enabled for the course has no standing to show")
    void aTutorNotEnabledIsNotFound() {
        when(reputation.standingOf(tutor, calculus)).thenReturn(Optional.empty());
        when(skills.isTutorEnabledFor(tutor, calculus)).thenReturn(false);

        assertThatThrownBy(() -> query.execute(tutor, calculus))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("does not teach this course");
    }
}
