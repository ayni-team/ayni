package pe.ayni.reputation.application;

import java.util.List;
import java.util.UUID;

/**
 * Public information shown when a student opens a tutor profile.
 */
public record TutorPublicProfile(
        UUID tutorId,
        String fullName,
        String career,
        String currentTerm,
        String photoUrl,
        List<Skill> enabledSkills) {

    public record Skill(
            UUID id,
            String name,
            String courseCode) {
    }
}