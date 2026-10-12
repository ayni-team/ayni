package pe.ayni.identity.application;

import java.util.List;
import java.util.Objects;

public record AcademicProfile(
        String fullName,
        String career,
        String currentTerm,
        List<AcademicCourseData> approvedCourses) {

    public AcademicProfile {
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