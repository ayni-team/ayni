package pe.ayni.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.ayni.identity.application.AcademicProfile;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

/** The simulated academic system of ADR 0006: one directory file per university, read on demand. */
class MockAcademicSystemAdapterTest {

    /** The folder the backend reads when it runs from {@code backend/}, with the demo students. */
    private static final Path DEMO_DIRECTORY = Path.of("academic-directory");

    private final ObjectMapper json = new ObjectMapper();

    private final MockAcademicSystemAdapter demo =
            new MockAcademicSystemAdapter(DEMO_DIRECTORY, json);

    @TempDir Path folder;

    @Test
    @DisplayName("Finds a student by university and institutional email, with the code of the file")
    void findsAStudentByEmail() {
        AcademicProfile profile = demo.fetchProfile("UPC", "u202400001@upc.edu.pe");

        assertThat(profile.studentCode()).isEqualTo("U202400001");
        assertThat(profile.fullName()).isEqualTo("Ana Torres");
        assertThat(profile.career()).isEqualTo("Software Engineering");
        assertThat(profile.currentTerm()).isEqualTo("2026-2");
        assertThat(profile.approvedCourses()).hasSize(3);
        assertThat(profile.approvedCourses().get(1).grade()).isEqualByComparingTo("16.00");
    }

    @Test
    @DisplayName("Keeps the four demonstration students of UPC and PUCP")
    void keepsTheDemoStudents() {
        assertThat(demo.fetchProfile("UPC", "u202300002@upc.edu.pe").fullName()).isEqualTo("Bruno Salas");
        assertThat(demo.fetchProfile("upc", "U202500003@UPC.EDU.PE").fullName()).isEqualTo("Carla Mendoza");
        AcademicProfile lucia = demo.fetchProfile("PUCP", "20240001@pucp.edu.pe");
        assertThat(lucia.studentCode()).isEqualTo("20240001");
        assertThat(lucia.fullName()).isEqualTo("Lucia Perez");
        assertThat(lucia.approvedCourses()).hasSize(2);
    }

    @Test
    @DisplayName("Copies the student code as the university writes it, whatever its format")
    void doesNotValidateTheCode() throws IOException {
        write("UNI", "nora.quispe@uni.edu.pe", "2026-0042/b");
        MockAcademicSystemAdapter adapter = new MockAcademicSystemAdapter(folder, json);

        assertThat(adapter.fetchProfile("UNI", " Nora.Quispe@uni.edu.pe ").studentCode())
                .isEqualTo("2026-0042/b");
    }

    @Test
    @DisplayName("Reads the file when it looks a student up, not when it starts")
    void readsTheFileOnDemand() throws IOException {
        MockAcademicSystemAdapter adapter = new MockAcademicSystemAdapter(folder, json);
        assertThatThrownBy(() -> adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("not configured");

        write("UNI", "nora.quispe@uni.edu.pe", "N1");
        assertThat(adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe").studentCode()).isEqualTo("N1");

        write("UNI", "nora.quispe@uni.edu.pe", "N2");
        assertThat(adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe").studentCode()).isEqualTo("N2");
    }

    @Test
    @DisplayName("An email the university does not know is not found, in that university only")
    void rejectsUnknownStudent() {
        assertThatThrownBy(() -> demo.fetchProfile("UPC", "u202499999@upc.edu.pe"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> demo.fetchProfile("PUCP", "u202400001@upc.edu.pe"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("A university code that would leave the folder names no file")
    void aCodeCannotLeaveTheFolder() {
        MockAcademicSystemAdapter adapter = new MockAcademicSystemAdapter(DEMO_DIRECTORY.resolve("sub"), json);

        assertThatThrownBy(() -> adapter.fetchProfile("../UPC", "u202400001@upc.edu.pe"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("A file that cannot be read fails as a mistake of the platform")
    void aBrokenFileIsAnUnexpectedFailure() throws IOException {
        Files.writeString(folder.resolve("UNI.json"), "{ not json");
        MockAcademicSystemAdapter adapter = new MockAcademicSystemAdapter(folder, json);

        assertThatThrownBy(() -> adapter.fetchProfile("UNI", "nora.quispe@uni.edu.pe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNI");
    }

    private void write(String university, String email, String code) throws IOException {
        Files.writeString(
                folder.resolve(university + ".json"),
                """
                {"students": [{"email": "%s", "studentCode": "%s", "fullName": "Nora Quispe",
                  "career": "Law", "currentTerm": "2026-2",
                  "approvedCourses": [{"courseCode": "DER101", "courseName": "Civil Law",
                                       "grade": %s, "term": "2026-1"}]}]}
                """
                        .formatted(email, code, new BigDecimal("15.50")));
    }
}
