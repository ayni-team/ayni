package pe.ayni.config;

import java.util.Locale;

/**
 * Tests only: small documents of the simulated academic system (ADR 0006), with the schema of
 * {@code tools/academic-data/README.md}, for the tests about reading a document rather than about
 * the data delivered in {@code backend/academic-directory}.
 *
 * <p>A university of code {@code X} claims {@code x.edu.pe}, teaches two careers, Derecho and
 * Economía, and three courses: {@code DER101} of Derecho, {@code ECO101} of Economía and {@code
 * GEN100} of both. Its minimum teaching grade is 14.5.
 */
public final class AcademicDocuments {

  private AcademicDocuments() {}

  /** The document of the university with that code and those students. */
  public static String document(String code, String... students) {
    String lower = code.toLowerCase(Locale.ROOT);
    return """
        {
          "university": {
            "code": "%s", "name": "Universidad %s", "logoUrl": "/logos/%s.png",
            "primaryColor": "#123456", "secondaryColor": "#FFFFFF",
            "emailDomains": ["%s.edu.pe"], "timezone": "America/Lima",
            "minimumTeachingGrade": 14.5, "currentTerm": "2026-2"
          },
          "careers": ["Derecho", "Economía"],
          "courses": [
            {"code": "DER101", "name": "Derecho Civil I", "careers": [{"career": "Derecho", "cycle": 2}]},
            {"code": "ECO101", "name": "Microeconomía", "careers": [{"career": "Economía", "cycle": 2}]},
            {"code": "GEN100", "name": "Lenguaje",
             "careers": [{"career": "Derecho", "cycle": 1}, {"career": "Economía", "cycle": 1}]}
          ],
          "students": [%s]
        }
        """
        .formatted(code, code, lower, lower, String.join(",", students));
  }

  /**
   * An active student of Derecho in cycle 3, named Nora Quispe, who approved {@code DER101} with
   * 15.50.
   */
  public static String student(String email, String studentCode) {
    return """
        {"email": "%s", "studentCode": "%s", "fullName": "Nora Quispe", "career": "Derecho",
         "cycle": 3, "currentTerm": "2026-2", "status": "ACTIVE",
         "approvedCourses": [{"courseCode": "DER101", "grade": 15.50, "term": "2026-1"}]}
        """
        .formatted(email, studentCode);
  }
}
