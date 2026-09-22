package pe.ayni.identity.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.identity.application.AcademicCourseData;
import pe.ayni.identity.application.AcademicSystemPort;

@Component
@Profile("dev")
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
                        "SI385",
                        "Fundamentos de Arquitectura de Software",
                        BigDecimal.valueOf(18),
                        "2026-2"),
                new AcademicCourseData(
                        "SI400",
                        "Aplicaciones Web",
                        BigDecimal.valueOf(17),
                        "2026-2"),
                new AcademicCourseData(
                        "SI392",
                        "Diseño de Experimentos",
                        BigDecimal.valueOf(16),
                        "2026-2"));
    }
}