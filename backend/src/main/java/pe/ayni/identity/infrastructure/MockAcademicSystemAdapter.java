package pe.ayni.identity.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.ayni.identity.application.AcademicCourseData;
import pe.ayni.identity.application.AcademicSystemPort;

/**
 * Stands in for the universities' academic systems until Ayni integrates with one.
 *
 * <p>It is active in every profile, not only in {@code dev}: it is the only implementation of
 * {@link AcademicSystemPort}, and without one the application context does not start, which takes
 * every other module's tests down with it. The first real adapter replaces it.
 *
 * <p>Every UPC student gets the same record. The course codes are the ones {@code DemoSkillsData}
 * puts in the catalogue, so that offering a course works end to end in {@code dev}: two grades
 * clear the demo threshold and one does not.
 */
@Component
public class MockAcademicSystemAdapter implements AcademicSystemPort {

    @Override
    public List<AcademicCourseData> findApprovedCourses(
            String tenantId,
            String studentCode) {

        if (!"UPC".equalsIgnoreCase(tenantId)) {
            return List.of();
        }

        return List.of(
                new AcademicCourseData(
                        "1ASI0657",
                        "Software Architecture Fundamentals",
                        new BigDecimal("18.00"),
                        "2026-1"),
                new AcademicCourseData(
                        "1ASI0616",
                        "Databases I",
                        new BigDecimal("16.00"),
                        "2025-2"),
                new AcademicCourseData(
                        "1MAT0101",
                        "Calculus I",
                        new BigDecimal("11.00"),
                        "2025-1"));
    }
}
