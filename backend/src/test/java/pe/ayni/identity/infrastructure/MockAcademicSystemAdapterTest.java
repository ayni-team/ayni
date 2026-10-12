package pe.ayni.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pe.ayni.identity.application.AcademicProfile;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

class MockAcademicSystemAdapterTest {

    private final MockAcademicSystemAdapter academicSystem =
            new MockAcademicSystemAdapter();

    @Test
    void returnsAcademicProfileByStudentCode() {
        AcademicProfile profile =
                academicSystem.fetchProfile(
                        "UPC",
                        "u202400001");

        assertThat(profile.fullName())
                .isEqualTo("Ana Torres");

        assertThat(profile.career())
                .isEqualTo("Software Engineering");

        assertThat(profile.currentTerm())
                .isEqualTo("2026-2");

        assertThat(profile.approvedCourses())
                .hasSize(3);
    }

    @Test
    void supportsSeveralUniversities() {
        AcademicProfile profile =
                academicSystem.fetchProfile(
                        "PUCP",
                        "20240001");

        assertThat(profile.fullName())
                .isEqualTo("Lucia Perez");

        assertThat(profile.career())
                .isEqualTo("Computer Science");

        assertThat(profile.approvedCourses())
                .hasSize(2);
    }

    @Test
    void appliesStudentCodeRulePerUniversity() {
        assertThatThrownBy(
                () ->
                        academicSystem.fetchProfile(
                                "PUCP",
                                "U202400001"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining(
                        "university rule");
    }

    @Test
    void rejectsUnknownStudent() {
        assertThatThrownBy(
                () ->
                        academicSystem.fetchProfile(
                                "UPC",
                                "U202499999"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining(
                        "not found");
    }
}