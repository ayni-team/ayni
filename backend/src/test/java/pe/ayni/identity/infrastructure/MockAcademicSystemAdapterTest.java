package pe.ayni.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.ayni.config.AcademicDocuments;
import pe.ayni.identity.application.AcademicProfile;
import pe.ayni.identity.application.AcademicStatus;
import pe.ayni.identity.application.AcademicUniversity;
import pe.ayni.identity.domain.model.AcademicSystemNotConfigured;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

/**
 * The simulated academic system of ADR 0006: one document per university, read on demand, without
 * Spring. The scenarios of {@code features/academic-system-documents.feature} about reading a
 * document run here, each with the same name; the documents are fixtures of the test, written into
 * a folder of its own, since what is tested is the reading and not the data delivered.
 */
class MockAcademicSystemAdapterTest {

    private final ObjectMapper json = new ObjectMapper();

    @TempDir Path folder;

    private MockAcademicSystemAdapter adapter() {
        return new MockAcademicSystemAdapter(folder, json);
    }

    @Test
    @DisplayName("A valid document is read and the course names come from its curriculum")
    void aValidDocumentIsRead() throws IOException {
        write("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student("nora.quispe@uni.edu.pe", "2026-0042/b")));

        AcademicProfile profile = adapter().fetchProfile("uni", " Nora.Quispe@UNI.edu.pe ");

        // The code as the university writes it, whatever its format.
        assertThat(profile.studentCode()).isEqualTo("2026-0042/b");
        assertThat(profile.fullName()).isEqualTo("Nora Quispe");
        assertThat(profile.career()).isEqualTo("Derecho");
        assertThat(profile.cycle()).isEqualTo(3);
        assertThat(profile.currentTerm()).isEqualTo("2026-2");
        assertThat(profile.status()).isEqualTo(AcademicStatus.ACTIVE);
        assertThat(profile.isEnrolled()).isTrue();
        // The student names the course by its code only; the name is the curriculum's.
        assertThat(profile.approvedCourses())
                .singleElement()
                .satisfies(
                        course -> {
                            assertThat(course.courseCode()).isEqualTo("DER101");
                            assertThat(course.courseName()).isEqualTo("Derecho Civil I");
                            assertThat(course.grade()).isEqualByComparingTo("15.50");
                            assertThat(course.term()).isEqualTo("2026-1");
                        });

        AcademicUniversity university = adapter().fetchUniversity("UNI");
        assertThat(university.code()).isEqualTo("UNI");
        assertThat(university.name()).isEqualTo("Universidad UNI");
        assertThat(university.logoUrl()).isEqualTo("/logos/uni.png");
        assertThat(university.primaryColor()).isEqualTo("#123456");
        assertThat(university.emailDomains()).containsExactly("uni.edu.pe");
        assertThat(university.timezone()).isEqualTo("America/Lima");
        assertThat(university.minimumTeachingGrade()).isEqualByComparingTo("14.5");
        assertThat(university.currentTerm()).isEqualTo("2026-2");
        assertThat(university.careers()).containsExactly("Derecho", "Economía");
        assertThat(university.courses())
                .extracting(AcademicUniversity.Course::code)
                .containsExactly("DER101", "ECO101", "GEN100");
        assertThat(university.courses().get(2).careers())
                .containsExactly(
                        new AcademicUniversity.Placement("Derecho", 1),
                        new AcademicUniversity.Placement("Economía", 1));
    }

    @Test
    @DisplayName("Reads the document when it is asked, not when it starts")
    void readsTheDocumentOnDemand() throws IOException {
        MockAcademicSystemAdapter adapter = adapter();
        assertThatThrownBy(() -> adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe"))
                .isInstanceOf(AcademicSystemNotConfigured.class);

        write("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1")));
        assertThat(adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe").studentCode()).isEqualTo("N1");

        write("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student("nora.quispe@uni.edu.pe", "N2")));
        assertThat(adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe").studentCode()).isEqualTo("N2");
    }

    @Test
    @DisplayName("An incoherent document is a mistake of the platform")
    void anIncoherentDocumentIsAMistakeOfThePlatform() throws IOException {
        String nora = AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1");

        // A course approved that is not in the curriculum of the student's career: ECO101 is
        // Economía's, and Nora studies Derecho.
        write("UNI", AcademicDocuments.document("UNI", nora.replace("DER101", "ECO101")));
        assertIncoherent("not in the curriculum of the student's career");

        // An email repeated, whatever its case.
        write(
                "UNI",
                AcademicDocuments.document(
                        "UNI", nora, AcademicDocuments.student("NORA.QUISPE@uni.edu.pe", "N2")));
        assertIncoherent("email is repeated");

        // An email of another domain.
        write("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student("nora.quispe@gmail.com", "N1")));
        assertIncoherent("not of a domain of the university");

        // A student code repeated, and a course code repeated.
        write(
                "UNI",
                AcademicDocuments.document(
                        "UNI", nora, AcademicDocuments.student("luis.rojas@uni.edu.pe", "N1")));
        assertIncoherent("student code is repeated");
        write("UNI", AcademicDocuments.document("UNI", nora).replace("\"ECO101\"", "\"DER101\""));
        assertIncoherent("course code is repeated");
    }

    @Test
    @DisplayName("A document that cannot be read fails as a mistake of the platform")
    void anUnreadableDocumentIsAMistakeOfThePlatform() throws IOException {
        Files.writeString(folder.resolve("UNI.json"), "{ not json");

        assertThatThrownBy(() -> adapter().fetchProfile("UNI", "nora.quispe@uni.edu.pe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNI")
                .hasMessageNotContaining("nora");
    }

    @Test
    @DisplayName("Without its document a university has no academic system")
    void withoutItsDocumentAUniversityHasNoAcademicSystem() throws IOException {
        write("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1")));

        assertThatThrownBy(() -> adapter().fetchProfile("OTRA", "nora.quispe@uni.edu.pe"))
                .isInstanceOf(AcademicSystemNotConfigured.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> adapter().fetchUniversity("OTRA"))
                .isInstanceOf(AcademicSystemNotConfigured.class);
        // A code that would leave the folder names no document either.
        assertThatThrownBy(() -> adapter().fetchUniversity("../UNI"))
                .isInstanceOf(AcademicSystemNotConfigured.class);
        // Nor does a local addition alone.
        Files.writeString(folder.resolve("SOLA.local.json"), "{\"students\": []}");
        assertThatThrownBy(() -> adapter().fetchUniversity("SOLA"))
                .isInstanceOf(AcademicSystemNotConfigured.class);
    }

    @Test
    @DisplayName("A document of another university is a mistake of the platform")
    void aDocumentOfAnotherUniversity() throws IOException {
        write("UNI", AcademicDocuments.document("OTRA", AcademicDocuments.student("nora.quispe@otra.edu.pe", "N1")));

        assertThatThrownBy(() -> adapter().fetchUniversity("UNI"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("of another university");
    }

    @Test
    @DisplayName("An email the university does not know is not found, in that university only")
    void anUnknownEmailIsNotFound() throws IOException {
        write("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1")));
        write("OTRA", AcademicDocuments.document("OTRA", AcademicDocuments.student("luis.rojas@otra.edu.pe", "L1")));

        assertThatThrownBy(() -> adapter().fetchProfile("UNI", "luis.rojas@otra.edu.pe"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("not found");
        assertThat(adapter().fetchProfile("OTRA", "luis.rojas@otra.edu.pe").studentCode()).isEqualTo("L1");
    }

    @Test
    @DisplayName("A student who is no longer enrolled is found with their status")
    void aStudentNoLongerEnrolledIsFoundWithTheirStatus() throws IOException {
        write(
                "UNI",
                AcademicDocuments.document(
                        "UNI",
                        AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1").replace("ACTIVE", "GRADUATED"),
                        AcademicDocuments.student("luis.rojas@uni.edu.pe", "L1").replace("ACTIVE", "inactive")));

        AcademicProfile nora = adapter().fetchProfile("UNI", "nora.quispe@uni.edu.pe");
        assertThat(nora.status()).isEqualTo(AcademicStatus.GRADUATED);
        assertThat(nora.isEnrolled()).isFalse();
        assertThat(adapter().fetchProfile("UNI", "luis.rojas@uni.edu.pe").status())
                .isEqualTo(AcademicStatus.INACTIVE);

        // A status the platform does not know makes the document incoherent.
        write(
                "UNI",
                AcademicDocuments.document(
                        "UNI", AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1").replace("ACTIVE", "ON_LEAVE")));
        assertIncoherent("status that is not known");
    }

    @Test
    @DisplayName("A local file adds students and wins on a repeated email")
    void aLocalFileAddsStudentsAndWins() throws IOException {
        write(
                "UNI",
                AcademicDocuments.document(
                        "UNI",
                        AcademicDocuments.student("nora.quispe@uni.edu.pe", "N1"),
                        AcademicDocuments.student("luis.rojas@uni.edu.pe", "L1")));
        // Only the students of the local file are read: its university, careers and courses are
        // ignored, so it may hold the students alone.
        Files.writeString(
                folder.resolve("UNI.local.json"),
                """
                {"students": [%s, %s]}
                """
                        .formatted(
                                AcademicDocuments.student("lead.person@uni.edu.pe", "LOCAL-1"),
                                AcademicDocuments.student("Luis.Rojas@uni.edu.pe", "L1")
                                        .replace("Nora Quispe", "Luis Rojas (local)")));

        assertThat(adapter().fetchProfile("UNI", "lead.person@uni.edu.pe").studentCode())
                .isEqualTo("LOCAL-1");
        assertThat(adapter().fetchProfile("UNI", "luis.rojas@uni.edu.pe").fullName())
                .isEqualTo("Luis Rojas (local)");
        assertThat(adapter().fetchProfile("UNI", "nora.quispe@uni.edu.pe").studentCode()).isEqualTo("N1");

        // The local students are checked like the others: one of another domain breaks the
        // document.
        Files.writeString(
                folder.resolve("UNI.local.json"),
                "{\"students\": [%s]}"
                        .formatted(AcademicDocuments.student("lead.person@gmail.com", "LOCAL-1")));
        assertIncoherent("not of a domain of the university");
    }

    private void assertIncoherent(String brokenRule) {
        assertThatThrownBy(() -> adapter().fetchUniversity("UNI"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNI")
                .hasMessageContaining(brokenRule)
                .hasMessageNotContaining("@");
    }

    private void write(String university, String document) throws IOException {
        Files.writeString(folder.resolve(university + ".json"), document);
    }
}
