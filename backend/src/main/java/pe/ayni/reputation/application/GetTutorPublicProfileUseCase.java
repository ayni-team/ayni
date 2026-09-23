package pe.ayni.reputation.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;


import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.skills.SkillsApi;

/**
 * Builds the public profile shown when a student compares tutors.
 */

public class GetTutorPublicProfileUseCase {

    private final IdentityApi identity;
    private final SkillsApi skills;

    GetTutorPublicProfileUseCase(IdentityApi identity, SkillsApi skills) {
        this.identity = identity;
        this.skills = skills;
    }

    public TutorPublicProfile execute(UUID tutorId) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");

        UserView tutor = identity.requireUser(tutorId);

        List<TutorPublicProfile.Skill> enabledSkills =
                skills.enabledSkillsOf(tutorId).stream()
                        .map(skills::requireItem)
                        .map(
                                item ->
                                        new TutorPublicProfile.Skill(
                                                item.id(),
                                                item.name(),
                                                item.courseCode()))
                        .toList();

        return new TutorPublicProfile(
                tutor.id(),
                tutor.fullName(),
                tutor.career(),
                tutor.currentTerm(),
                tutor.photoUrl(),
                enabledSkills);
    }
}