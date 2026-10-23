package pe.ayni.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
import pe.ayni.identity.application.AcademicUniversity;
import pe.ayni.identity.domain.model.AcademicSystemNotConfigured;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

/**
 * Simulates the academic systems of the affiliated universities (ADR 0006), one document per
 * university.
 *
 * <p>The folder is {@code ayni.identity.academic-directory}; the document of a university is its
 * code in upper case, {@code UPC.json}, with the schema of {@link AcademicDocument}. It is read each
 * time it is asked, never at startup, so a document added or edited is used at once: nothing is
 * changed in the code and nothing is deployed again. A student is found by their institutional
 * email, whatever its case, and the profile carries the student code the document says. The adapter
 * does not check the format of a code: that is the university's business.
 *
 * <p>Next to a document there may be {@code <CODE>.local.json}, which the repository never keeps:
 * its students are added to the ones of the document, and one whose email is in both is the local
 * one. Only its list of students is read.
 *
 * <p>Every document read is checked with {@link AcademicDocument#checkCoherence} first. One that
 * cannot be read or does not agree with itself is a mistake of the platform: the log says so with
 * the code of the university and the rule that was broken, and nothing about any student.
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

        Objects.requireNonNull(
                institutionalEmail,
                "institutionalEmail must not be null");

        AcademicDocument document =
                documentOf(normalizeCode(tenantId));

        return document.student(institutionalEmail)
                .map(student -> profileOf(student, document))
                .orElseThrow(
                        () ->
                                new IdentityRuleViolation(
                                        "The student was not found "
                                                + "in the academic system"));
    }

    @Override
    public AcademicUniversity fetchUniversity(
            String code) {

        AcademicDocument document =
                documentOf(normalizeCode(code));
        AcademicDocument.University university =
                document.university();

        return new AcademicUniversity(
                university.code().trim().toUpperCase(Locale.ROOT),
                university.name().trim(),
                university.logoUrl(),
                university.primaryColor(),
                university.secondaryColor(),
                university.emailDomains(),
                university.timezone(),
                university.minimumTeachingGrade(),
                university.currentTerm(),
                document.careers(),
                document.courses().stream()
                        .map(
                                course ->
                                        new AcademicUniversity.Course(
                                                course.code(),
                                                course.name(),
                                                course.careers().stream()
                                                        .map(
                                                                placement ->
                                                                        new AcademicUniversity.Placement(
                                                                                placement.career(),
                                                                                placement.cycle()))
                                                        .toList()))
                        .toList());
    }

    /** The document of the university with its local students, checked. */
    AcademicDocument documentOf(String code) {
        if (!SAFE_CODE.matcher(code).matches()) {
            throw new AcademicSystemNotConfigured();
        }
        Path file = directory.resolve(code + ".json");
        if (!Files.isRegularFile(file)) {
            throw new AcademicSystemNotConfigured();
        }

        AcademicDocument document = read(code, file);
        Path local = directory.resolve(code + ".local.json");
        if (Files.isRegularFile(local)) {
            document = document.withLocalStudents(read(code, local).students());
        }

        try {
            document.checkCoherence(code);
        } catch (AcademicDocument.Incoherent incoherent) {
            // A document that contradicts itself is a configuration mistake of the platform, not
            // the student's: the log names the university and the rule, never a person.
            log.warn(
                    "The academic directory of university {} is not coherent: {}",
                    code,
                    incoherent.getMessage());
            throw new IllegalStateException(
                    "The academic directory of university " + code + " is not coherent: "
                            + incoherent.getMessage());
        }
        return document;
    }

    private AcademicDocument read(String code, Path file) {
        try {
            return json.readValue(file.toFile(), AcademicDocument.class);
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

    private static AcademicProfile profileOf(
            AcademicDocument.Student student,
            AcademicDocument document) {

        List<AcademicCourseData> approved =
                student.approved().stream()
                        .map(
                                course ->
                                        new AcademicCourseData(
                                                course.courseCode(),
                                                document.courseName(course.courseCode()),
                                                course.grade(),
                                                course.term()))
                        .toList();

        return new AcademicProfile(
                student.studentCode().trim(),
                student.fullName().trim(),
                student.career(),
                student.cycle(),
                student.currentTerm(),
                AcademicDocument.statusOf(student),
                approved);
    }

    private static String normalizeCode(
            String code) {

        return Objects.requireNonNull(
                        code,
                        "code must not be null")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
