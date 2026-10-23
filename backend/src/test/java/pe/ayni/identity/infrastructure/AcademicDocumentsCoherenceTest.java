package pe.ayni.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.application.AcademicStatus;

/**
 * The documents the repository delivers, {@code backend/academic-directory/UPC.json} and {@code
 * USIL.json}, read and checked with the same logic as the adapter ({@link
 * MockAcademicSystemAdapter#documentOf}), without Spring. The scenario of {@code
 * features/academic-system-documents.feature} with the same name.
 *
 * <p>A local addition next to them ({@code <CODE>.local.json}) would be read too, as the adapter
 * reads it; the repository never has one.
 */
class AcademicDocumentsCoherenceTest {

    /** The folder the backend reads when it runs from {@code backend/}. */
    private static final Path DIRECTORY = Path.of("academic-directory");

    private final MockAcademicSystemAdapter adapter =
            new MockAcademicSystemAdapter(DIRECTORY, new ObjectMapper());

    @Test
    @DisplayName("The documents delivered with the repository are coherent")
    void theDeliveredDocumentsAreCoherent() {
        for (String code : List.of("UPC", "USIL")) {
            // The adapter's own check: it throws when the document breaks a rule.
            AcademicDocument document = adapter.documentOf(code);

            assertThat(document.university().code()).isEqualTo(code);
            assertThat(document.courses()).as(code).isNotEmpty();
            assertThat(document.students()).as(code).isNotEmpty();

            // Every approved course is behind the student's cycle: nobody approved a course of
            // the cycle they are in, or of a later one.
            for (AcademicDocument.Student student : document.students()) {
                Map<String, Integer> cycleInCareer = cyclesOf(document, student.career());
                for (AcademicDocument.ApprovedCourse approved : student.approved()) {
                    assertThat(cycleInCareer.get(approved.courseCode()))
                            .as("%s, %s", code, approved.courseCode())
                            .isLessThan(student.cycle());
                }
            }
        }
    }

    @Test
    @DisplayName("The documents hold the people the demonstration is told with")
    void theDocumentsHoldThePeopleOfTheDemonstration() {
        assertThat(adapter.fetchProfile("UPC", "u202400001@upc.edu.pe").fullName()).isEqualTo("Ana Torres");
        assertThat(adapter.fetchProfile("UPC", "u202300002@upc.edu.pe").fullName()).isEqualTo("Bruno Salas");
        assertThat(adapter.fetchProfile("UPC", "u202500003@upc.edu.pe").fullName()).isEqualTo("Carla Mendoza");
        assertThat(adapter.fetchProfile("USIL", "lucia.paredes@usil.edu.pe").fullName())
                .isEqualTo("Lucía Paredes");
        assertThat(adapter.fetchProfile("USIL", "mateo.rios@usil.edu.pe").fullName()).isEqualTo("Mateo Ríos");

        // Each university has somebody its academic system knows but no longer counts as a student.
        for (String code : List.of("UPC", "USIL")) {
            assertThat(adapter.documentOf(code).students())
                    .extracting(AcademicDocument::statusOf)
                    .as(code)
                    .contains(AcademicStatus.ACTIVE)
                    .containsAnyOf(AcademicStatus.GRADUATED, AcademicStatus.INACTIVE);
        }
    }

    private static Map<String, Integer> cyclesOf(AcademicDocument document, String career) {
        Map<String, Integer> cycles = new HashMap<>();
        for (AcademicDocument.Course course : document.courses()) {
            course.careers().stream()
                    .filter(placement -> placement.career().equals(career))
                    .forEach(placement -> cycles.put(course.code(), placement.cycle()));
        }
        return cycles;
    }
}
