package pe.ayni.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>The document of a university is its code in upper case, {@code UPC.json}, with the schema of
 * {@link AcademicDocument}. It comes from an {@link AcademicDocumentSource}: the folder {@code
 * ayni.identity.academic-directory}, or, when {@code ayni.identity.academic-directory-url} is set,
 * that address of another service ({@link AcademicSystemConfig}). It is read when it is asked,
 * never at startup, so a document added or edited is used without changing the code or deploying
 * again: at once from the folder, within a minute from the address. A student is found by their institutional
 * email, whatever its case, and the profile carries the student code the document says. The adapter
 * does not check the format of a code: that is the university's business.
 *
 * <p>In the folder there may be {@code <CODE>.local.json}, which the repository never keeps: its
 * students are added to the ones of the document, and one whose email is in both is the local one.
 * Only its list of students is read. It is always read from the folder, also when the document
 * comes from an address, so a real person added for a demonstration is never published.
 *
 * <p>Every document read is checked with {@link AcademicDocument#checkCoherence} first. One that
 * cannot be read or does not agree with itself is a mistake of the platform: the log says so with
 * the code of the university and the rule that was broken, and nothing about any student.
 */
public class MockAcademicSystemAdapter
        implements AcademicSystemPort {

    private static final Logger log =
            LoggerFactory.getLogger(MockAcademicSystemAdapter.class);

    /** What a university code may hold to name a file: nothing that leaves the folder. */
    private static final Pattern SAFE_CODE =
            Pattern.compile("[A-Z0-9_-]{1,32}");

    private final AcademicDocumentSource documents;
    private final AcademicDocumentSource localStudents;
    private final ObjectMapper json;

    /** Documents and local students, both from the folder. */
    public MockAcademicSystemAdapter(
            Path directory,
            ObjectMapper json) {

        this(
                new FolderAcademicDocumentSource(directory),
                directory,
                json);
    }

    /** Documents from {@code documents}, local students from the folder. */
    MockAcademicSystemAdapter(
            AcademicDocumentSource documents,
            Path directory,
            ObjectMapper json) {

        this.documents =
                Objects.requireNonNull(
                        documents,
                        "documents must not be null");
        this.localStudents =
                new FolderAcademicDocumentSource(directory);
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
        AcademicDocument document =
                read(code, documents, code + ".json")
                        .orElseThrow(AcademicSystemNotConfigured::new);
        Optional<AcademicDocument> local =
                read(code, localStudents, code + ".local.json");
        if (local.isPresent()) {
            document = document.withLocalStudents(local.get().students());
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

    private Optional<AcademicDocument> read(
            String code,
            AcademicDocumentSource source,
            String name) {

        try {
            Optional<byte[]> content = source.read(name);
            if (content.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(json.readValue(content.get(), AcademicDocument.class));
        } catch (IOException unreadable) {
            // A broken file, or an address that does not answer, is a configuration mistake of the
            // platform, not the student's: it is said in the log, by its name only, and the request
            // fails as unexpected.
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
