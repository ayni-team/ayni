package pe.ayni.reputation.application;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.skills.SkillsApi;

/**
 * US02: a tutor's standing in one course, as a student comparing tutors sees it.
 *
 * <p>A tutor who never taught the course has no row in {@code reputation.tutor_standing}: the row
 * appears with the first completed session. That tutor is not missing, they are new, and US02 says a
 * new tutor is shown without an average, the same way the search already shows them. So when there
 * is no row but skills says the tutor may teach the course, the answer is a standing with nothing
 * counted. Only a tutor who is not enabled for the course has no standing to show.
 *
 * <p>A tutor who taught the course and was later withdrawn from it keeps the standing they earned:
 * the row is history, and history is not erased because the offer ended.
 */
@Service
public class TutorStandingQuery {

    private final ReputationApi reputation;
    private final SkillsApi skills;

    TutorStandingQuery(ReputationApi reputation, SkillsApi skills) {
        this.reputation = reputation;
        this.skills = skills;
    }

    /**
     * @throws NoSuchElementException when the tutor has no standing in the course and is not enabled
     *     to teach it in the current university
     */
    @Transactional(readOnly = true)
    public TutorStandingView execute(UUID tutorId, UUID catalogItemId) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");
        Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");

        return reputation
                .standingOf(tutorId, catalogItemId)
                .orElseGet(() -> newTutorOrNotFound(tutorId, catalogItemId));
    }

    private TutorStandingView newTutorOrNotFound(UUID tutorId, UUID catalogItemId) {
        if (!skills.isTutorEnabledFor(tutorId, catalogItemId)) {
            throw new NoSuchElementException(
                    "The tutor does not teach this course: there is no standing to show");
        }
        return new TutorStandingView(tutorId, catalogItemId, 0, 0, null);
    }
}
