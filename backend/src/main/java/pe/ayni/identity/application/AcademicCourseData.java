package pe.ayni.identity.application;

import java.math.BigDecimal;

public record AcademicCourseData(
        String courseCode,
        String courseName,
        BigDecimal grade,
        String term) {}