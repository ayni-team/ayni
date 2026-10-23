package pe.ayni.identity.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Who a university is and what it teaches, as its academic system says (ADR 0006).
 *
 * <p>It is what a university is registered with and what its curriculum is synchronised from. The
 * logo is a path or an address as the university gives it: a path relative to the origin of the
 * frontend, such as {@code /logos/upc.png}, is kept as it comes.
 *
 * @param minimumTeachingGrade the grade needed in a course to teach it, on the scale of 0 to 20
 * @param currentTerm the academic term in course, such as {@code 2026-2}
 * @param careers the careers of the university, by name
 * @param courses its curriculum: every course once, with each career it belongs to and its cycle
 *     there
 */
public record AcademicUniversity(
        String code,
        String name,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        List<String> emailDomains,
        String timezone,
        BigDecimal minimumTeachingGrade,
        String currentTerm,
        List<String> careers,
        List<Course> courses) {

    public AcademicUniversity {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        emailDomains = List.copyOf(Objects.requireNonNull(emailDomains, "emailDomains must not be null"));
        careers = List.copyOf(Objects.requireNonNull(careers, "careers must not be null"));
        courses = List.copyOf(Objects.requireNonNull(courses, "courses must not be null"));
    }

    /** A course of the curriculum, identified by its code. */
    public record Course(String code, String name, List<Placement> careers) {

        public Course {
            careers = List.copyOf(careers);
        }
    }

    /** Where a course sits in one career: the career and the cycle it is taught in. */
    public record Placement(String career, int cycle) {}
}
