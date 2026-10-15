package pe.ayni.identity.application;

import java.util.List;
import java.util.Objects;

/**
 * What a university's academic system says about one of its students (ADR 0006).
 *
 * <p>The student code is the university's, as its directory keeps it: Ayni copies it and never
 * derives it nor checks its format, because every university writes its codes its own way.
 */
public record AcademicProfile(
        String studentCode,
        String fullName,
        String career,
        String currentTerm,
        List<AcademicCourseData> approvedCourses) {

    public AcademicProfile {
        studentCode =
                Objects.requireNonNull(
                        studentCode,
                        "studentCode must not be null");

        if (studentCode.isBlank()) {
            throw new IllegalArgumentException(
                    "studentCode must not be blank");
        }

        fullName =
                Objects.requireNonNull(
                        fullName,
                        "fullName must not be null");

        career =
                Objects.requireNonNull(
                        career,
                        "career must not be null");

        currentTerm =
                Objects.requireNonNull(
                        currentTerm,
                        "currentTerm must not be null");

        approvedCourses =
                List.copyOf(
                        Objects.requireNonNull(
                                approvedCourses,
                                "approvedCourses must not be null"));
    }
}
