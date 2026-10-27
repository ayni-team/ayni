package pe.ayni.identity;

import java.math.BigDecimal;

/** A course the academic system reports as approved. */
public record ApprovedCourseView(
    String courseCode, String courseName, BigDecimal grade, String term) {}
