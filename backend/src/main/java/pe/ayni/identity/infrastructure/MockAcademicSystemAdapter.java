package pe.ayni.identity.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pe.ayni.identity.application.AcademicCourseData;
import pe.ayni.identity.application.AcademicProfile;
import pe.ayni.identity.application.AcademicSystemPort;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

/**
 * Simulates the academic systems of affiliated universities.
 *
 * <p>One adapter serves several universities. Each university has
 * its own student-code rule and academic profiles.
 */
@Component
public class MockAcademicSystemAdapter
        implements AcademicSystemPort {

    private static final Map<String, UniversityAcademicData>
            UNIVERSITIES =
            Map.of(
                    "UPC",
                    upc(),
                    "PUCP",
                    pucp());

    @Override
    public AcademicProfile fetchProfile(
            String tenantId,
            String studentCode) {

        String normalizedTenant =
                normalizeTenant(tenantId);

        String normalizedCode =
                normalizeStudentCode(studentCode);

        UniversityAcademicData university =
                UNIVERSITIES.get(normalizedTenant);

        if (university == null) {
            throw new IdentityRuleViolation(
                    "The academic system is not configured "
                            + "for this university");
        }

        if (!university
                .studentCodeRule()
                .matcher(normalizedCode)
                .matches()) {

            throw new IdentityRuleViolation(
                    "The student code does not match "
                            + "the university rule");
        }

        AcademicProfile profile =
                university
                        .profiles()
                        .get(normalizedCode);

        if (profile == null) {
            throw new IdentityRuleViolation(
                    "The student was not found "
                            + "in the academic system");
        }

        return profile;
    }

    private static UniversityAcademicData upc() {
        List<AcademicCourseData> courses =
                List.of(
                        course(
                                "1ASI0657",
                                "Software Architecture Fundamentals",
                                "18.00",
                                "2026-1"),
                        course(
                                "1ASI0616",
                                "Databases I",
                                "16.00",
                                "2025-2"),
                        course(
                                "1MAT0101",
                                "Calculus I",
                                "11.00",
                                "2025-1"));

        return new UniversityAcademicData(
                Pattern.compile("(?i)U\\d{9}"),
                Map.of(
                        "U202400001",
                        new AcademicProfile(
                                "Ana Torres",
                                "Software Engineering",
                                "2026-2",
                                courses),

                        "U202300002",
                        new AcademicProfile(
                                "Bruno Salas",
                                "Software Engineering",
                                "2026-2",
                                courses)));
    }

    private static UniversityAcademicData pucp() {
        return new UniversityAcademicData(
                Pattern.compile("\\d{8}"),
                Map.of(
                        "20240001",
                        new AcademicProfile(
                                "Lucia Perez",
                                "Computer Science",
                                "2026-2",
                                List.of(
                                        course(
                                                "INF226",
                                                "Software Engineering",
                                                "18.00",
                                                "2026-1"),
                                        course(
                                                "INF263",
                                                "Databases",
                                                "16.00",
                                                "2025-2")))));
    }

    private static AcademicCourseData course(
            String code,
            String name,
            String grade,
            String term) {

        return new AcademicCourseData(
                code,
                name,
                new BigDecimal(grade),
                term);
    }

    private static String normalizeTenant(
            String tenantId) {

        return Objects.requireNonNull(
                        tenantId,
                        "tenantId must not be null")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeStudentCode(
            String studentCode) {

        return Objects.requireNonNull(
                        studentCode,
                        "studentCode must not be null")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private record UniversityAcademicData(
            Pattern studentCodeRule,
            Map<String, AcademicProfile> profiles) {}
}