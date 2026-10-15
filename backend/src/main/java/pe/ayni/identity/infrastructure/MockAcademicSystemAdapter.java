package pe.ayni.identity.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.identity.application.AcademicCourseData;
import pe.ayni.identity.application.AcademicProfile;
import pe.ayni.identity.application.AcademicSystemPort;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

/**
 * Simulates the academic systems of the affiliated universities (ADR 0006), one directory file per
 * university.
 *
 * <p>The folder is {@code ayni.identity.academic-directory}; the file of a university is its code
 * in upper case, {@code UPC.json}. It is read each time a student is looked up, never at startup,
 * so a university registered by configuration (TS10) is known as soon as its file is in the
 * folder: nothing is changed in the code and nothing is deployed again. A student is found by their
 * institutional email, whatever its case, and the profile carries the student code the file says.
 * The adapter does not check the format of a code: that is the university's business.
 *
 * <p>The file holds a {@code students} array; each student has {@code email}, {@code
 * studentCode}, {@code fullName}, {@code career}, {@code currentTerm} and {@code approvedCourses},
 * each course with {@code courseCode}, {@code courseName}, {@code grade} and {@code term}.
 */
@Component
public class MockAcademicSystemAdapter
        implements AcademicSystemPort {

    private static final Logger log =
            LoggerFactory.getLogger(MockAcademicSystemAdapter.class);

    /** What a university code may hold to name a file: nothing that leaves the folder. */
    private static final Pattern SAFE_CODE =
            Pattern.compile("[A-Z0-9_-]{1,32}");

    private final Path directory;
    private final ObjectMapper json;

    public MockAcademicSystemAdapter(
            @Value("${ayni.identity.academic-directory}") Path directory,
            ObjectMapper json) {

        this.directory =
                Objects.requireNonNull(
                        directory,
                        "directory must not be null");
        this.json =
                Objects.requireNonNull(
                        json,
                        "json must not be null");
    }

    @Override
    public AcademicProfile fetchProfile(
            String tenantId,
            String institutionalEmail) {

        String code = normalizeTenant(tenantId);
        String email = normalizeEmail(institutionalEmail);

        Path file = fileOf(code);
        if (file == null || !Files.isRegularFile(file)) {
            throw new IdentityRuleViolation(
                    "The academic system is not configured "
                            + "for this university");
        }

        return readDirectory(code, file).students().stream()
                .filter(student -> student.email() != null)
                .filter(student -> student.email().trim().toLowerCase(Locale.ROOT).equals(email))
                .findFirst()
                .map(MockAcademicSystemAdapter::profileOf)
                .orElseThrow(
                        () ->
                                new IdentityRuleViolation(
                                        "The student was not found "
                                                + "in the academic system"));
    }

    private Path fileOf(String code) {
        if (!SAFE_CODE.matcher(code).matches()) {
            return null;
        }
        return directory.resolve(code + ".json");
    }

    private Directory readDirectory(String code, Path file) {
        try {
            Directory read = json.readValue(file.toFile(), Directory.class);
            return read.students() == null ? new Directory(List.of()) : read;
        } catch (IOException unreadable) {
            // A broken file is a configuration mistake of the platform, not the student's: it is
            // said in the log, by its name only, and the request fails as unexpected.
            log.warn(
                    "The academic directory of university {} cannot be read: {}",
                    code,
                    unreadable.getClass().getSimpleName());
            throw new IllegalStateException(
                    "The academic directory of university " + code + " cannot be read",
                    unreadable);
        }
    }

    private static AcademicProfile profileOf(Student student) {
        return new AcademicProfile(
                student.studentCode(),
                student.fullName(),
                student.career(),
                student.currentTerm(),
                student.approvedCourses() == null
                        ? List.of()
                        : student.approvedCourses().stream()
                                .map(
                                        course ->
                                                new AcademicCourseData(
                                                        course.courseCode(),
                                                        course.courseName(),
                                                        course.grade(),
                                                        course.term()))
                                .toList());
    }

    private static String normalizeTenant(
            String tenantId) {

        return Objects.requireNonNull(
                        tenantId,
                        "tenantId must not be null")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeEmail(
            String institutionalEmail) {

        return Objects.requireNonNull(
                        institutionalEmail,
                        "institutionalEmail must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** The file of one university. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Directory(List<Student> students) {}

    /** One student as the university's directory keeps them. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Student(
            String email,
            String studentCode,
            String fullName,
            String career,
            String currentTerm,
            List<Course> approvedCourses) {}

    /** One approved course with its grade. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Course(
            String courseCode,
            String courseName,
            BigDecimal grade,
            String term) {}
}
