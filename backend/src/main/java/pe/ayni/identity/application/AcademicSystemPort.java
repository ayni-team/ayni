package pe.ayni.identity.application;

import java.util.List;

public interface AcademicSystemPort {

    List<AcademicCourseData> findApprovedCourses(
            String tenantId,
            String studentCode);
}